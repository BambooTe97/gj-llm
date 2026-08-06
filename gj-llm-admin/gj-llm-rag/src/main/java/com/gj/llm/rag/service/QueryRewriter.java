package com.gj.llm.rag.service;

import com.gj.llm.rag.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询改写服务 -- 用 LLM 将用户口语化查询改写为正式风格的检索查询。
 *
 * <p>解决口语化提问（"这玩意儿咋配"）与知识库文档（"配置方法如下..."）之间的语义鸿沟。</p>
 *
 * <p>改写是检索自己的事,rag 自配改写模型({@link RagProperties#getRewriteModel()}),
 * 通过 Spring AI 的 {@link ChatModel} 调用,不绑定具体 Provider,不手写 HTTP。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class QueryRewriter {

    private final ChatClient chatClient;
    private final String rewriteModel;

    private static final String REWRITE_PROMPT = """
            将以下口语化问题改写为2个不同角度的正式检索查询，每行一个，不要编号和解释。

            口语：%s
            改写查询：""";

    private static final String HYDE_PROMPT = """
            根据以下问题，写一段可能在知识库文档中找到的答案段落。\
            不需要真实准确，只需要风格和术语像知识库文档。直接输出段落，不要解释。

            问题：%s
            答案段落：""";

    public QueryRewriter(ChatModel chatModel, RagProperties ragProperties) {
        this.chatClient = ChatClient.create(chatModel);
        this.rewriteModel = ragProperties.getRewriteModel();
    }

    /**
     * 将口语化查询改写为多个检索变体（含原始查询 + 书面改写 + HyDE 假设答案），
     * 扩大粗排候选覆盖面。HyDE 假设答案的段落风格与知识库文档对齐，
     * embedding 匹配度远高于短查询。
     *
     * @param query 用户原始查询
     * @return 检索查询变体列表，失败时返回仅含原始查询的列表
     */
    public List<String> rewrite(String query) {
        if (query == null || query.isBlank()) {
            return List.of(query);
        }

        List<String> variants = new ArrayList<>();
        variants.add(query); // 原始查询放第一位（保底）

        // 生成书面语改写
        for (String line : callLLM(REWRITE_PROMPT.formatted(query), 128)) {
            String cleaned = line.replaceFirst("^\\d+[\\.\\)、]\\s*", "").trim();
            if (!cleaned.isBlank() && !cleaned.equals(query) && !variants.contains(cleaned)) {
                variants.add(cleaned);
            }
        }

        // 生成 HyDE 假设答案段落 -- 风格接近真实文档，embedding 匹配度最高
        for (String answer : callLLM(HYDE_PROMPT.formatted(query), 256)) {
            String cleaned = answer.trim();
            if (!cleaned.isBlank() && !variants.contains(cleaned)) {
                variants.add(cleaned);
            }
        }

        log.info("查询改写: {} -> {} 个变体 (含HyDE)", query.substring(0, Math.min(query.length(), 40)),
                variants.size());
        return variants;
    }

    /** 调用 LLM，返回按行拆分的结果 */
    private List<String> callLLM(String prompt, int maxTokens) {
        try {
            OllamaChatOptions.Builder options = OllamaChatOptions.builder()
                    .numPredict(maxTokens)
                    .disableThinking(); // 改写任务不需要思考,省 token
            if (rewriteModel != null && !rewriteModel.isBlank()) {
                options.model(rewriteModel);
            }
            String content = chatClient.prompt()
                    .messages(new UserMessage(prompt))
                    .options(options)
                    .call()
                    .content();
            if (content != null && !content.isBlank()) {
                List<String> lines = new ArrayList<>();
                for (String line : content.trim().split("\n")) {
                    if (!line.isBlank()) {
                        lines.add(line.trim());
                    }
                }
                return lines;
            }
        } catch (Exception e) {
            log.warn("LLM 调用失败: {}", e.getMessage());
        }
        return List.of();
    }
}
