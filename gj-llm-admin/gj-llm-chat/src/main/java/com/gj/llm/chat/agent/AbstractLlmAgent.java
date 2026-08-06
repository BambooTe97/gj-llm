package com.gj.llm.chat.agent;

import com.gj.llm.chat.config.ChatProperties;
import com.gj.llm.chat.sse.SseEventBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 内部 LLM 智能体的模板方法基类 -- 收口"调模型 -> 流式 -> 提取 thinking -> 发 SSE"公共流程。
 *
 * <p>Ollama 私有细节(thinking 元数据键、{@link OllamaChatOptions})只收敛在此类,
 * 不泄漏到子类与编排器。子类只需提供 {@link #prepare} 组装前置事件 + Prompt。</p>
 *
 * <p>thinking 事件发累积全文(前端做替换展示),content 事件发增量 chunk(前端追加)。</p>
 *
 * @author gj-llm
 */
@Slf4j
public abstract class AbstractLlmAgent implements Agent {

    /** Ollama 在 AssistantMessage metadata 中存放思考内容的键 */
    private static final String THINKING_METADATA_KEY = "thinking";

    protected final ChatClient chatClient;
    protected final ChatProperties chatProperties;

    protected AbstractLlmAgent(ChatModel chatModel, ChatProperties chatProperties) {
        this.chatClient = ChatClient.create(chatModel);
        this.chatProperties = chatProperties;
    }

    /** 子类组装:前置事件(引用/no_result/检索提示等)+ 送入 LLM 的消息序列 */
    protected abstract PreparedPrompt prepare(AgentContext ctx);

    @Override
    public final Flux<ServerSentEvent<String>> stream(AgentContext ctx) {
        return Flux.defer(() -> {
            PreparedPrompt pp = prepare(ctx);
            ChatProperties.AgentConfig cfg = chatProperties.getAgents().get(id());
            boolean think = ctx.isEnableThinking() && (cfg == null || cfg.isThinking());
            Flux<ServerSentEvent<String>> llm = streamLlm(ctx, pp.messages(), cfg, think);
            return Flux.concat(Flux.fromIterable(pp.preEvents()), llm);
        });
    }

    /** 流式调用 LLM,提取 content/thinking 并发 SSE,同时累积到 ctx 缓冲 */
    private Flux<ServerSentEvent<String>> streamLlm(AgentContext ctx, List<Message> messages,
                                                    ChatProperties.AgentConfig cfg, boolean think) {
        OllamaChatOptions.Builder options = OllamaChatOptions.builder();
        if (cfg != null) {
            if (cfg.getModel() != null && !cfg.getModel().isBlank()) {
                options.model(cfg.getModel());
            }
            if (cfg.getNumPredict() != null) {
                options.numPredict(cfg.getNumPredict());
            }
        }
        if (!think) {
            options.disableThinking();
        }

        return chatClient.prompt()
                .messages(messages)
                .options(options)
                .stream()
                .chatResponse()
                .concatMap(resp -> {
                    List<ServerSentEvent<String>> events = new ArrayList<>();
                    AssistantMessage msg = resp.getResult().getOutput();

                    // 提取思考增量,累积后发全文(前端替换展示)
                    Object thinkObj = msg.getMetadata() == null ? null : msg.getMetadata().get(THINKING_METADATA_KEY);
                    if (thinkObj != null) {
                        String thinkChunk = String.valueOf(thinkObj);
                        if (!thinkChunk.isEmpty()) {
                            ctx.getFullThinking().append(thinkChunk);
                            events.add(SseEventBuilder.event("thinking", Map.of("content", ctx.getFullThinking().toString())));
                        }
                    }

                    // 提取内容增量,发 chunk(前端追加)
                    String contentChunk = msg.getText();
                    if (contentChunk != null && !contentChunk.isEmpty()) {
                        ctx.getFullAnswer().append(contentChunk);
                        events.add(SseEventBuilder.event("content", Map.of("content", contentChunk)));
                    }

                    return Flux.fromIterable(events);
                })
                .doOnError(e -> log.error("[Agent:{}] 流式调用出错", id(), e));
    }

    /** 把历史消息 + 当前用户消息拼成 Spring AI Message 序列(system 由子类放入 messages 首位) */
    protected List<Message> buildMessageList(String systemPrompt, String userPrompt, AgentContext ctx) {
        List<Message> messages = new ArrayList<>();
        messages.add(new org.springframework.ai.chat.messages.SystemMessage(systemPrompt));
        for (com.gj.llm.chat.entity.MessageEntity m : ctx.getHistory()) {
            String content = m.getContent() != null ? m.getContent() : "";
            if ("assistant".equals(m.getRole())) {
                messages.add(new AssistantMessage(content));
            } else {
                messages.add(new org.springframework.ai.chat.messages.UserMessage(content));
            }
        }
        messages.add(new org.springframework.ai.chat.messages.UserMessage(userPrompt));
        return messages;
    }

    /** prepare 的返回结构 */
    protected record PreparedPrompt(List<ServerSentEvent<String>> preEvents, List<Message> messages) {
    }
}
