package com.gj.llm.chat.service.impl;

import com.gj.llm.chat.entity.ConversationEntity;
import com.gj.llm.chat.entity.MessageEntity;
import com.gj.llm.chat.mapper.ConversationMapper;
import com.gj.llm.chat.mapper.MessageMapper;
import com.gj.llm.chat.model.ChatRequest;
import com.gj.llm.chat.service.ChatService;
import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.rag.vector.DynamicVectorStoreManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    private final DynamicVectorStoreManager storeManager;
    private final DatasetService datasetService;
    private final ChatClient.Builder chatClientBuilder;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public ChatServiceImpl(DynamicVectorStoreManager storeManager,
                           DatasetService datasetService,
                           ChatClient.Builder chatClientBuilder,
                           ConversationMapper conversationMapper,
                           MessageMapper messageMapper) {
        this.storeManager = storeManager;
        this.datasetService = datasetService;
        this.chatClientBuilder = chatClientBuilder;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public Flux<ServerSentEvent<String>> chatStream(ChatRequest request) {
        return Flux.defer(() -> {
            Long conversationId = request.getConversationId();
            String userContent = request.getContent();

            // 1. 验证会话存在
            ConversationEntity conversation = conversationMapper.selectById(conversationId);
            if (conversation == null) {
                return Flux.just(buildEvent("error", Map.of("message", "会话不存在: " + conversationId)));
            }

            // 2. 保存用户消息
            MessageEntity userMsg = MessageEntity.builder()
                    .conversationId(conversationId)
                    .role("user")
                    .content(userContent)
                    .createdAt(LocalDateTime.now())
                    .build();
            messageMapper.insert(userMsg);

            // 3. RAG 检索
            Long datasetId = request.getDatasetId() != null ? request.getDatasetId() : conversation.getDatasetId();
            String context = "";
            List<Map<String, Object>> references = List.of();

            if (datasetId != null) {
                try {
                    DatasetEntity dataset = datasetService.getById(datasetId);
                    if (dataset != null) {
                        VectorStore vectorStore = storeManager.getVectorStore(dataset.getCollectionName());
                        List<Document> docs = vectorStore.similaritySearch(
                                SearchRequest.builder()
                                        .query(userContent)
                                        .topK(5)
                                        .similarityThreshold(0.7)
                                        .build());
                        context = docs.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
                        references = buildReferences(docs);
                    }
                } catch (Exception e) {
                    log.warn("RAG 检索失败，继续通用对话: {}", e.getMessage());
                }
            }

            // 4. 构建 Prompt
            String systemPrompt = buildSystemPrompt(context);
            List<MessageEntity> history = getRecentHistory(conversationId, 10);
            String prompt = buildUserPrompt(history, userContent, context);

            // 5. 构建前置事件 Flux
            List<ServerSentEvent<String>> preEvents = new ArrayList<>();
            if (datasetId != null) {
                preEvents.add(buildEvent("thinking", Map.of("content", "正在检索知识库...")));
            }
            if (!references.isEmpty()) {
                preEvents.add(buildEvent("references", Map.of("items", references)));
            }

            // 6. 流式调用 LLM
            StringBuilder fullAnswer = new StringBuilder();
            Flux<String> contentFlux = chatClientBuilder
                    .defaultSystem(systemPrompt)
                    .build()
                    .prompt()
                    .user(prompt)
                    .stream()
                    .content()
                    .doOnNext(fullAnswer::append);

            Flux<ServerSentEvent<String>> contentEvents = contentFlux
                    .map(chunk -> buildEvent("content", Map.of("content", chunk)));

            // 7. 完成后保存 assistant 消息、更新会话、发送 done 事件
            Flux<ServerSentEvent<String>> doneEvent = Flux.defer(() -> {
                MessageEntity assistantMsg = MessageEntity.builder()
                        .conversationId(conversationId)
                        .role("assistant")
                        .content(fullAnswer.toString())
                        .createdAt(LocalDateTime.now())
                        .build();
                messageMapper.insert(assistantMsg);

                int newCount = (conversation.getMessageCount() != null ? conversation.getMessageCount() : 0) + 2;
                conversation.setMessageCount(newCount);
                if (newCount <= 2 && "新对话".equals(conversation.getTitle())) {
                    String autoTitle = userContent.length() > 30
                            ? userContent.substring(0, 30) + "..."
                            : userContent;
                    conversation.setTitle(autoTitle);
                }
                conversationMapper.updateById(conversation);

                return Flux.just(buildEvent("done", Map.of(
                        "messageId", assistantMsg.getId(),
                        "conversationId", conversationId,
                        "title", conversation.getTitle()
                )));
            });

            return Flux.concat(
                    Flux.fromIterable(preEvents),
                    contentEvents,
                    doneEvent
            ).onErrorResume(e -> {
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

    private String buildUserPrompt(List<MessageEntity> history, String currentQuestion, String context) {
        StringBuilder sb = new StringBuilder();
        if (context != null && !context.isBlank()) {
            sb.append("参考上下文:\n").append(context).append("\n\n");
        }
        if (!history.isEmpty()) {
            sb.append("历史对话:\n");
            for (MessageEntity msg : history) {
                String roleLabel = "assistant".equals(msg.getRole()) ? "AI" : "用户";
                sb.append(roleLabel).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }
        sb.append("用户问题:\n").append(currentQuestion);
        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    private List<Map<String, Object>> buildReferences(List<Document> docs) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            double score = doc.getScore() != null ? doc.getScore() : 0.0;
            String text = doc.getText();
            refs.add(Map.of(
                    "rank", i + 1,
                    "content", text != null ? text.substring(0, Math.min(text.length(), 200)) : "",
                    "score", Math.round(score * 1000.0) / 1000.0
            ));
        }
        return refs;
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
}
