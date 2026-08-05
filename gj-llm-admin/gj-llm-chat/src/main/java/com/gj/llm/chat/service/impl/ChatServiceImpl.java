package com.gj.llm.chat.service.impl;

import com.gj.llm.chat.entity.ConversationEntity;
import com.gj.llm.chat.entity.MessageEntity;
import com.gj.llm.chat.mapper.ConversationMapper;
import com.gj.llm.chat.mapper.MessageMapper;
import com.gj.llm.chat.model.ChatRequest;
import com.gj.llm.chat.service.ChatService;
import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.es.service.EsSearchService;
import com.gj.llm.reranker.service.RerankerService;
import com.gj.llm.rag.config.RagProperties;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.rag.service.QueryRewriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import java.util.stream.Collectors;

/**
 * 对话服务实现 —— RAG 增强的流式对话（基于 Reactor Flux）。
 *
 * <p>SSE 事件类型：thinking / references / content / done / error</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private final EsSearchService esSearchService;
    private final RerankerService rerankerService;
    private final QueryRewriter queryRewriter;
    private final DatasetService datasetService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final WebClient.Builder webClientBuilder;
    private final RagProperties ragProperties;

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.model}")
    private String chatModel;

    /** Ollama 生成 token 上限（thinking + content），防止无限思考 */
    private static final int NUM_PREDICT_LIMIT = 4096;

    /** RAG 检索最终返回数量 */
    private static final int TOP_K = 5;

    /** 每个查询变体的粗排返回数 */
    private static final int VARIANT_TOP_K = 8;

    /** 合并后送 re-ranker 的最大候选数 */
    private static final int MAX_RERANK_CANDIDATES = 30;

    public ChatServiceImpl(EsSearchService esSearchService,
                           RerankerService rerankerService,
                           QueryRewriter queryRewriter,
                           DatasetService datasetService,
                           ConversationMapper conversationMapper,
                           MessageMapper messageMapper,
                           WebClient.Builder webClientBuilder,
                           RagProperties ragProperties) {
        this.esSearchService = esSearchService;
        this.rerankerService = rerankerService;
        this.queryRewriter = queryRewriter;
        this.datasetService = datasetService;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.webClientBuilder = webClientBuilder;
        this.ragProperties = ragProperties;
    }

    @Override
    public Flux<ServerSentEvent<String>> chatStream(ChatRequest request) {
        return Flux.defer(() -> {
            long t0 = System.currentTimeMillis();
            Long conversationId = request.getConversationId();
            String userContent = request.getContent();
            log.info("[chatStream] ========== 开始, conversationId={}, content.length()={}", conversationId, userContent.length());

            // 1. 验证会话存在
            long t1 = System.currentTimeMillis();
            ConversationEntity conversation = conversationMapper.selectById(conversationId);
            if (conversation == null) {
                return Flux.just(buildEvent("error", Map.of("message", "会话不存在: " + conversationId)));
            }
            log.info("[chatStream] ① DB查询会话完成, 耗时: {}ms", System.currentTimeMillis() - t1);

            // 2. 保存用户消息
            long t2 = System.currentTimeMillis();
            MessageEntity userMsg = MessageEntity.builder()
                    .conversationId(conversationId)
                    .role("user")
                    .content(userContent)
                    .createdAt(LocalDateTime.now())
                    .build();
            messageMapper.insert(userMsg);
            log.info("[chatStream] ② DB插入用户消息完成, 耗时: {}ms", System.currentTimeMillis() - t2);

            // 3. RAG 检索
            long t3 = System.currentTimeMillis();
            Long datasetId = request.getDatasetId() != null ? request.getDatasetId() : conversation.getDatasetId();
            String context = "";
            List<Map<String, Object>> references = List.of();
            boolean noConfidentResult = false;

            if (datasetId != null) {
                try {
                    long t3a = System.currentTimeMillis();
                    DatasetEntity dataset = datasetService.getById(datasetId);
                    log.info("[chatStream] ③a DB查询数据集完成, 耗时: {}ms", System.currentTimeMillis() - t3a);

                    if (dataset != null) {
                        // ① 查询改写：生成多个检索变体（含原始查询），扩大粗排覆盖面
                        long t3r = System.currentTimeMillis();
                        List<String> queries = queryRewriter.rewrite(userContent);
                        long rewriteCost = System.currentTimeMillis() - t3r;
                        log.info("[chatStream] ③r 查询改写完成, 变体数={}, 耗时: {}ms",
                                queries.size(), rewriteCost);

                        // ② 多路粗排：每个变体分别检索，合并去重
                        long t3b = System.currentTimeMillis();
                        Map<String, Document> merged = new LinkedHashMap<>();
                        for (String q : queries) {
                            List<Document> hits = esSearchService.hybridSearch(dataset.getCollectionName(), q, VARIANT_TOP_K);
                            for (Document doc : hits) {
                                String key = doc.getText().trim();
                                Document existing = merged.get(key);
                                if (existing == null || doc.getScore() > existing.getScore()) {
                                    merged.put(key, doc);
                                }
                            }
                        }
                        List<Document> candidates = new ArrayList<>(merged.values());
                        candidates.sort(Comparator.comparingDouble(Document::getScore).reversed());
                        if (candidates.size() > MAX_RERANK_CANDIDATES) {
                            candidates = candidates.subList(0, MAX_RERANK_CANDIDATES);
                        }
                        log.info("[chatStream] ③b 多路检索完成, 合并去重后候选 {} 条, 耗时: {}ms",
                                candidates.size(), System.currentTimeMillis() - t3b);

                        // ③ 精排：Cross-Encoder Re-Ranker 重打分
                        long t3c = System.currentTimeMillis();
                        List<Document> docs = rerankerService.rerank(userContent, candidates, TOP_K);
                        log.info("[chatStream] ③c Re-Ranker 精排完成, 返回 {} 条, 耗时: {}ms",
                                docs.size(), System.currentTimeMillis() - t3c);

                        // ④ 父子召回：按 parent_id 去重（同父块保留最高分）
                        List<Document> deduped = dedupByParent(docs);
                        // ⑤ 质量护栏：过滤低于 rerank 阈值的弱结果
                        double threshold = ragProperties.getRerankScoreThreshold();
                        List<Document> confident = deduped.stream()
                                .filter(d -> scoreOf(d) >= threshold)
                                .toList();
                        if (confident.isEmpty()) {
                            // 无可靠答案：不把弱上下文塞给 LLM 编造，走"我不知道"分支
                            noConfidentResult = true;
                            log.info("[chatStream] ③d 无可靠检索结果（rerank 阈值 {}），走我不知道分支", threshold);
                        } else {
                            context = buildParentContext(confident);
                            references = buildReferences(confident);
                            log.info("[chatStream] ③d 父子召回: {} -> {} 父块, 可信 {} 条, context.len={}",
                                    docs.size(), deduped.size(), confident.size(), context.length());
                        }
                    }
                } catch (Exception e) {
                    log.warn("RAG 检索失败，继续通用对话: {}", e.getMessage());
                }
            }
            log.info("[chatStream] ③ RAG 检索总耗时: {}ms", System.currentTimeMillis() - t3);

            // 4. 构建 Prompt
            long t4 = System.currentTimeMillis();
            String systemPrompt = noConfidentResult ? buildNoResultPrompt() : buildSystemPrompt(context);
            List<MessageEntity> history = getRecentHistory(conversationId, 10);
            // 当前用户问题（仅含 RAG 上下文，历史通过独立 message 角色传递）
            String userPrompt = buildUserPrompt(userContent, context);
            log.info("[chatStream] ④ Prompt 构建完成, history.size()={}, userPrompt.length()={}, 耗时: {}ms",
                    history.size(), userPrompt.length(), System.currentTimeMillis() - t4);

            // 5. 构建前置事件 Flux
            List<ServerSentEvent<String>> preEvents = new ArrayList<>();
            if (datasetId != null) {
                preEvents.add(buildEvent("thinking", Map.of("content", "正在检索知识库...")));
            }
            if (noConfidentResult) {
                preEvents.add(buildEvent("no_result", Map.of("message", "知识库中未找到与该问题相关的内容")));
            }
            if (!references.isEmpty()) {
                preEvents.add(buildEvent("references", Map.of("items", references)));
            }

            // 6. 流式调用 LLM（直接用 WebClient 调 Ollama，捕获 thinking 字段）
            long t6 = System.currentTimeMillis();
            StringBuffer fullThinking = new StringBuffer();
            StringBuffer fullAnswer = new StringBuffer();

            // 构建 Ollama messages：system → 历史(user/assistant交替) → 当前问题
            List<Map<String, Object>> ollamaMessages = new ArrayList<>();
            ollamaMessages.add(Map.of("role", "system", "content", systemPrompt));
            for (MessageEntity msg : history) {
                String role = "assistant".equals(msg.getRole()) ? "assistant" : "user";
                ollamaMessages.add(Map.of("role", role, "content",
                        msg.getContent() != null ? msg.getContent() : ""));
            }
            ollamaMessages.add(Map.of("role", "user", "content", userPrompt));

            // 构建 Ollama 请求体
            Map<String, Object> ollamaRequest = new LinkedHashMap<>();
            ollamaRequest.put("model", chatModel);
            ollamaRequest.put("stream", true);
            ollamaRequest.put("messages", ollamaMessages);
            ollamaRequest.put("options", Map.of("num_predict", NUM_PREDICT_LIMIT));
            // think 是 Ollama API 的顶层字段，不在 options 里！
            boolean enableThinking = request.getEnableThinking() == null || request.getEnableThinking();
            if (!enableThinking) {
                ollamaRequest.put("think", false);
            }

            long[] firstThinkArr = new long[]{0};
            long[] firstContentArr = new long[]{0};
            int[] thinkCount = new int[]{0};
            int[] contentCount = new int[]{0};

            Flux<ServerSentEvent<String>> llmEventFlux = webClientBuilder
                    .baseUrl(ollamaBaseUrl)
                    .build()
                    .post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(ollamaRequest)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    // 行缓冲：跨 DataBuffer 拼接不完整的 NDJSON 行
                    .concatMap(new LineBuffer()::feed)
                    .filter(line -> !line.isBlank())
                    .map(JacksonUtils::readTree)
                    .concatMap(jsonNode -> {
                        List<ServerSentEvent<String>> events = new ArrayList<>();

                        // 提取 message.done 字段区分是否最后一帧
                        boolean done = jsonNode.has("done") && jsonNode.get("done").asBoolean(false);

                        // 提取 message.thinking
                        String thinkChunk = JacksonUtils.extractNestedString(jsonNode, "message", "thinking");
                        if (thinkChunk != null && !thinkChunk.isEmpty()) {
                            thinkCount[0]++;
                            fullThinking.append(thinkChunk);
                            long now = System.currentTimeMillis();
                            if (firstThinkArr[0] == 0) {
                                firstThinkArr[0] = now;
                                log.info("[chatStream] ⑥ ★ 第一个 thinking chunk 到达! 距LLM调用开始: {}ms, chunk.len={}",
                                        now - t6, thinkChunk.length());
                            }
                            // 发送累积的完整 thinking 内容（前端做替换展示）
                            events.add(buildEvent("thinking", Map.of("content", fullThinking.toString())));
                        }

                        // 提取 message.content
                        String contentChunk = JacksonUtils.extractNestedString(jsonNode, "message", "content");
                        if (contentChunk != null && !contentChunk.isEmpty()) {
                            contentCount[0]++;
                            fullAnswer.append(contentChunk);
                            long now = System.currentTimeMillis();
                            if (firstContentArr[0] == 0) {
                                firstContentArr[0] = now;
                                log.info("[chatStream] ⑥ ★ 第一个 content chunk 到达! 距LLM调用开始: {}ms, chunk.len={}",
                                        now - t6, contentChunk.length());
                            }
                            events.add(buildEvent("content", Map.of("content", contentChunk)));
                        }

                        // 流结束帧
                        if (done) {
                            log.info("[chatStream] ⑥ LLM 流式结束, thinkChunks={}, contentChunks={}, thinking.len={}, content.len={}, 总耗时: {}ms",
                                    thinkCount[0], contentCount[0], fullThinking.length(), fullAnswer.length(),
                                    System.currentTimeMillis() - t6);
                        }

                        return Flux.fromIterable(events);
                    })
                    .doOnError(e -> log.error("[chatStream] ⑥ LLM 流式出错, thinkChunks={}, contentChunks={}, 耗时: {}ms",
                            thinkCount[0], contentCount[0], System.currentTimeMillis() - t6, e));

            // 7. 完成后保存 assistant 消息（含 thinking）、更新会话、发送 done 事件
            Flux<ServerSentEvent<String>> doneEvent = Flux.defer(() -> {
                long t7 = System.currentTimeMillis();
                MessageEntity assistantMsg = MessageEntity.builder()
                        .conversationId(conversationId)
                        .role("assistant")
                        .content(fullAnswer.toString())
                        .thinking(fullThinking.isEmpty() ? null : fullThinking.toString())
                        .createdAt(LocalDateTime.now())
                        .build();
                messageMapper.insert(assistantMsg);
                log.info("[chatStream] ⑦a DB保存assistant消息完成 (thinking.len={}), 耗时: {}ms",
                        fullThinking.length(), System.currentTimeMillis() - t7);

                long t7b = System.currentTimeMillis();
                int newCount = (conversation.getMessageCount() != null ? conversation.getMessageCount() : 0) + 2;
                conversation.setMessageCount(newCount);
                if (newCount <= 2 && "新对话".equals(conversation.getTitle())) {
                    String autoTitle = userContent.length() > 30
                            ? userContent.substring(0, 30) + "..."
                            : userContent;
                    conversation.setTitle(autoTitle);
                }
                conversationMapper.updateById(conversation);
                log.info("[chatStream] ⑦b DB更新会话完成, 耗时: {}ms", System.currentTimeMillis() - t7b);
                log.info("[chatStream] ========== 全部完成, 总耗时: {}ms, thinking.len={}, answer.len()={}",
                        System.currentTimeMillis() - t0, fullThinking.length(), fullAnswer.length());

                Map<String, Object> donePayload = new LinkedHashMap<>();
                donePayload.put("messageId", assistantMsg.getId());
                donePayload.put("conversationId", conversationId);
                donePayload.put("title", conversation.getTitle());
                if (!fullThinking.isEmpty()) {
                    donePayload.put("thinking", fullThinking.toString());
                }
                return Flux.just(buildEventWithMap(donePayload));
            });

            log.info("[chatStream] ⑤ 前置准备全部完成, 即将拼接Flux并开始LLM调用, 准备阶段总耗时: {}ms", System.currentTimeMillis() - t0);

            return Flux.concat(
                    Flux.fromIterable(preEvents),
                    llmEventFlux,
                    doneEvent
            ).doOnCancel(() -> {
                // 用户点击停止或断开连接 → 保存已生成的部分内容
                if (fullAnswer.length() > 0 || fullThinking.length() > 0) {
                    log.info("[chatStream] 请求被取消, 保存部分内容, thinking.len={}, answer.len={}",
                            fullThinking.length(), fullAnswer.length());
                    try {
                        MessageEntity partialMsg = MessageEntity.builder()
                                .conversationId(conversationId)
                                .role("assistant")
                                .content(fullAnswer.toString())
                                .thinking(fullThinking.isEmpty() ? null : fullThinking.toString())
                                .createdAt(LocalDateTime.now())
                                .build();
                        messageMapper.insert(partialMsg);
                        int newCount = (conversation.getMessageCount() != null ? conversation.getMessageCount() : 0) + 1;
                        conversation.setMessageCount(newCount);
                        conversationMapper.updateById(conversation);
                    } catch (Exception ex) {
                        log.error("[chatStream] 保存取消时的部分内容失败", ex);
                    }
                }
            }).onErrorResume(e -> {
                log.error("LLM 流式调用异常", e);
                return Flux.just(buildEvent("error", Map.of("message", "生成回复失败: " + e.getMessage())));
            });
        });
    }

    // ==================== Prompt 构建 ====================

    private String buildSystemPrompt(String context) {
        if (context != null && !context.isBlank()) {
            return """
                    你是一个智能知识库助手。请根据【参考上下文】回答用户的问题。
                    如果上下文中没有答案或信息不足，请诚实地告诉用户你不知道，不要编造。
                    回答时请保持专业、准确、简洁。
                    """;
        }
        return """
                你是一个智能AI助手。请根据你的知识回答用户的问题。
                保持专业、准确、友好的回答风格。
                """;
    }

    /** 无可靠检索结果时的系统提示 -- 引导 LLM 诚实告知而非编造。 */
    private String buildNoResultPrompt() {
        return """
                你是一个智能知识库助手。当前知识库中未检索到与用户问题相关的内容。
                请告知用户：知识库中暂无相关内容，无法回答该问题。
                可以建议用户换一种提问方式，或补充相关文档到知识库。不要编造答案。
                """;
    }

    /** 构建当前用户消息（含 RAG 上下文，历史对话通过独立 message 角色传递） */
    private String buildUserPrompt(String currentQuestion, String context) {
        if (context != null && !context.isBlank()) {
            return "参考上下文:\n" + context + "\n\n用户问题:\n" + currentQuestion;
        }
        return currentQuestion;
    }

    // ==================== 辅助方法 ====================

    private List<Map<String, Object>> buildReferences(List<Document> docs) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            double score = doc.getScore() != null ? doc.getScore() : 0.0;
            String text = doc.getText();
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("rank", i + 1);
            ref.put("content", text != null ? text.substring(0, Math.min(text.length(), 200)) : "");
            ref.put("score", Math.round(score * 1000.0) / 1000.0);
            ref.put("source", doc.getMetadata().get("source"));
            ref.put("datasetFileId", doc.getMetadata().get("dataset_file_id"));
            refs.add(ref);
        }
        return refs;
    }

    /**
     * 父子召回去重：按 parent_id 合并同父块的子块（保留最高分）。
     * parent_id 为 null（旧数据）时视为独立块，不合并，保持向后兼容。
     */
    private List<Document> dedupByParent(List<Document> docs) {
        Map<String, Document> byParent = new LinkedHashMap<>();
        for (Document d : docs) {
            String pid = (String) d.getMetadata().get("parent_id");
            String key = pid != null ? pid : "child:" + d.getId();
            Document ex = byParent.get(key);
            if (ex == null || scoreOf(d) > scoreOf(ex)) {
                byParent.put(key, d);
            }
        }
        List<Document> result = new ArrayList<>(byParent.values());
        result.sort(Comparator.comparingDouble(this::scoreOf).reversed());
        return result;
    }

    private double scoreOf(Document d) {
        return d.getScore() != null ? d.getScore() : 0.0;
    }

    /**
     * 用父块完整上下文构建 LLM context（parent_content 为 null 时回退子块文本）。
     * 受 {@link RagProperties#getContextBudgetChars()} 字符预算约束，防止溢出 gemma2:2b 的 8k token 窗口。
     */
    private String buildParentContext(List<Document> deduped) {
        int budget = ragProperties.getContextBudgetChars();
        StringBuilder ctx = new StringBuilder();
        for (Document d : deduped) {
            String parentContent = (String) d.getMetadata().get("parent_content");
            String seg = (parentContent != null && !parentContent.isBlank()) ? parentContent : d.getText();
            if (ctx.length() > 0 && ctx.length() + seg.length() + 2 > budget) {
                break; // 超预算截断，保留已累加的高分父块
            }
            if (ctx.length() > 0) {
                ctx.append("\n\n");
            }
            ctx.append(seg);
        }
        return ctx.toString();
    }

    private List<MessageEntity> getRecentHistory(Long conversationId, int maxPairs) {
        List<MessageEntity> all = messageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MessageEntity>()
                        .eq(MessageEntity::getConversationId, conversationId)
                        .orderByDesc(MessageEntity::getCreatedAt));
        int limit = maxPairs * 2;
        List<MessageEntity> recent = all.size() > limit ? all.subList(0, limit) : all;
        Collections.reverse(recent);
        return recent;
    }

    private ServerSentEvent<String> buildEvent(String type, Object data) {
        Map<String, Object> payload = new LinkedHashMap<>(JacksonUtils.toMap(data));
        payload.put("type", type);
        return ServerSentEvent.builder(JacksonUtils.toJson(payload)).build();
    }

    /** 直接用 Map 构建 SSE 事件（适用于手动构建 payload 的场景） */
    private ServerSentEvent<String> buildEventWithMap(Map<String, Object> payload) {
        return ServerSentEvent.builder(JacksonUtils.toJson(payload)).build();
    }

    // ==================== NDJSON 行缓冲 ====================

    /**
     * NDJSON 行缓冲器 —— 跨 {@link DataBuffer} 边界拼接不完整的行。
     *
     * <p>Ollama 的流式响应是 NDJSON 格式（每行一个完整 JSON），但底层 TCP 分帧
     * 可能导致一个 JSON 行被切分到两个 DataBuffer 中。此缓冲器将未完成的行尾部
     * 保留到下一次 {@link #feed(DataBuffer)} 调用时继续拼接。</p>
     */
    private static class LineBuffer {
        private String tail = "";

        Flux<String> feed(DataBuffer dataBuffer) {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            String text = tail + new String(bytes, StandardCharsets.UTF_8);
            String[] lines = text.split("\n", -1);
            // 最后一段是不完整的行（或空串），留到下一次拼接
            tail = lines[lines.length - 1];
            if (lines.length == 1) {
                // 没有遇到换行符，整段都是不完整的
                return Flux.empty();
            }
            return Flux.fromArray(Arrays.copyOf(lines, lines.length - 1));
        }
    }

}
