package com.gj.llm.chat.agent;

import com.gj.llm.chat.config.ChatProperties;
import com.gj.llm.chat.entity.MessageEntity;
import com.gj.llm.chat.sse.SseEventBuilder;
import com.gj.llm.common.util.JacksonUtils;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部智能体适配器 -- 把外部/市面 HTTP 智能体(Dify、Coze、自建服务等)包成 {@link Agent}。
 *
 * <p>配置驱动:在 {@code gj.llm.chat.agents.<id>} 声明 {@code type: remote} + endpoint + 鉴权头,
 * 即可接入,无需写代码。默认不配置即不启用,不影响现有对话流程。</p>
 *
 * <p>响应约定(通用,OpenAI/SSE 兼容):按行读取,识别 {@code data: {json}} 行,
 * 从 JSON 中取 {@code content}(或 {@code delta.content})作为增量答案,转成 content 事件,
 * 并累积到 {@link AgentContext} 供编排器持久化。厂商私有格式可按需扩展解析逻辑。</p>
 *
 * @author gj-llm
 */
@Slf4j
public class RemoteHttpAgent implements Agent {

    private final String agentId;
    private final ChatProperties.AgentConfig config;
    private final WebClient.Builder webClientBuilder;

    public RemoteHttpAgent(String agentId, ChatProperties.AgentConfig config, WebClient.Builder webClientBuilder) {
        this.agentId = agentId;
        this.config = config;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public String id() {
        return agentId;
    }

    @Override
    public Flux<ServerSentEvent<String>> stream(AgentContext ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", ctx.getUserContent());
        body.put("conversationId", ctx.getConversation() != null ? ctx.getConversation().getId() : null);

        // 透传历史,供外部智能体做多轮
        List<Map<String, String>> history = new ArrayList<>();
        for (MessageEntity m : ctx.getHistory()) {
            history.add(Map.of(
                    "role", m.getRole() != null ? m.getRole() : "user",
                    "content", m.getContent() != null ? m.getContent() : ""));
        }
        body.put("history", history);

        WebClient.RequestHeadersSpec<?> request = webClientBuilder
                .baseUrl(config.getEndpoint())
                .build()
                .post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
        if (config.getAuthHeader() != null && !config.getAuthHeader().isBlank()) {
            request = request.header("Authorization", config.getAuthHeader());
        }

        return request.retrieve()
                .bodyToFlux(String.class)
                .concatMap(line -> parseLine(line, ctx))
                .doOnError(e -> log.error("[RemoteAgent:{}] 调用出错", agentId, e))
                .onErrorResume(e -> Flux.just(SseEventBuilder.event("error",
                        Map.of("message", "外部智能体调用失败: " + e.getMessage()))));
    }

    /** 解析单行:识别 SSE data 行,提取 content 增量,累积到 ctx 并发事件 */
    private Flux<ServerSentEvent<String>> parseLine(String line, AgentContext ctx) {
        if (line == null || line.isBlank()) {
            return Flux.empty();
        }
        String jsonStr = null;
        if (line.startsWith("data:")) {
            jsonStr = line.substring(5).trim();
        } else if (line.startsWith("{")) {
            jsonStr = line;
        }
        if (jsonStr == null || "[DONE]".equals(jsonStr)) {
            return Flux.empty();
        }
        String content = null;
        try {
            JsonNode node = JacksonUtils.readTree(jsonStr);
            content = JacksonUtils.extractNestedString(node, "content");
            if (content == null) {
                content = JacksonUtils.extractNestedString(node, "delta", "content");
            }
        } catch (Exception ignored) {
            // 非 JSON,当作纯文本增量
            content = line;
        }
        if (content == null || content.isEmpty()) {
            return Flux.empty();
        }
        ctx.getFullAnswer().append(content);
        return Flux.just(SseEventBuilder.event("content", Map.of("content", content)));
    }
}
