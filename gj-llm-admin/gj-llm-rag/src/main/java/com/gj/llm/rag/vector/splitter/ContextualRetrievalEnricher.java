package com.gj.llm.rag.vector.splitter;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.rag.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 可选：LLM Contextual Retrieval（Anthropic 风格）-- 入库时调 LLM 给每个父块生成一句
 * "这段在文档里的位置/作用"上下文，拼到子块前缀，进一步提升 embedding 命中率。
 *
 * <p><b>默认关闭</b>：gemma2:2b 生成的上下文质量不确定，必须先用 {@link com.gj.llm.rag.eval.RetrievalEvaluator}
 * 评测证明有正收益再开启（{@code gj.llm.rag.contextual-retrieval.enabled=true}）。
 * 关闭时 {@link #enrich} 为空操作。</p>
 *
 * @author zf
 */
@Slf4j
@Component
public class ContextualRetrievalEnricher {

    private final RagProperties props;
    private final WebClient.Builder webClientBuilder;

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.model}")
    private String chatModel;

    public ContextualRetrievalEnricher(RagProperties props, WebClient.Builder webClientBuilder) {
        this.props = props;
        this.webClientBuilder = webClientBuilder;
    }

    private static final String CONTEXT_PROMPT = """
            下面是文档中的一个片段。请用一句话简述它在文档中可能的位置或主题，\
            便于检索匹配。只输出该句，不要解释。

            文档来源：%s
            片段：%s
            上下文：""";

    /**
     * 对每个父块生成一次 LLM 上下文并拼到其所有子块前缀。默认关闭时直接返回。
     */
    public void enrich(List<Chunk> chunks) {
        if (!props.isContextualRetrievalEnabled() || CollectionUtils.isEmpty(chunks)) {
            return;
        }
        // 按 parentId 分组，每个父块只调一次 LLM
        Map<String, List<Chunk>> byParent = chunks.stream().collect(Collectors.groupingBy(Chunk::getParentId));
        for (List<Chunk> group : byParent.values()) {
            Chunk first = group.get(0);
            String source = str(first.getMetadata().get("source"));
            String context = callLLM(CONTEXT_PROMPT.formatted(source, truncate(first.getParentText(), 800)), 96);
            if (context != null && !context.isBlank()) {
                String prefix = "[" + context.trim() + "]\n";
                group.forEach(c -> c.prependText(prefix));
            }
        }
        log.info("Contextual Retrieval 完成: 处理 {} 个父块", byParent.size());
    }

    private String callLLM(String prompt, int maxTokens) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", chatModel);
            body.put("stream", false);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("options", Map.of("num_predict", maxTokens));

            String resp = webClientBuilder.build()
                    .post()
                    .uri(ollamaBaseUrl + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (resp != null) {
                JsonNode node = JacksonUtils.readTree(resp);
                String content = JacksonUtils.extractNestedString(node, "message", "content");
                return content != null ? content.trim() : null;
            }
        } catch (Exception e) {
            log.warn("Contextual Retrieval LLM 调用失败: {}", e.getMessage());
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
