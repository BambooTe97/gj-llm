package com.gj.llm.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gj.llm.chat.agent.Agent;
import com.gj.llm.chat.agent.AgentContext;
import com.gj.llm.chat.agent.AgentRouter;
import com.gj.llm.chat.entity.ConversationEntity;
import com.gj.llm.chat.entity.MessageEntity;
import com.gj.llm.chat.mapper.MessageMapper;
import com.gj.llm.chat.model.ChatRequest;
import com.gj.llm.chat.service.ChatService;
import com.gj.llm.chat.service.ConversationService;
import com.gj.llm.chat.sse.SseEventBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话编排器 -- 替代原单线流水线,做顶层编排:校验 -> 存用户消息 -> 路由智能体 -> 收尾持久化。
 *
 * <p>编排器只管"流程串联 + 持久化 + 流式收发",不碰检索(归 rag)、不碰模型调用细节(归智能体)、
 * 不碰工具(归 mcp)。智能体产出事件流,编排器追加 done 事件并持久化。</p>
 *
 * <p>SSE 事件类型(与前端协议一致):thinking / references / content / no_result / error / done。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationService conversationService;
    private final MessageMapper messageMapper;
    private final AgentRouter agentRouter;

    @Override
    public Flux<ServerSentEvent<String>> chatStream(ChatRequest request) {
        return Flux.defer(() -> {
            long t0 = System.currentTimeMillis();
            Long conversationId = request.getConversationId();
            String userContent = request.getContent();
            log.info("[chatStream] ========== 开始, conversationId={}, content.length()={}",
                    conversationId, userContent.length());

            // 1. 校验会话
            ConversationEntity conversation = conversationService.getById(conversationId);
            if (conversation == null) {
                return Flux.just(SseEventBuilder.event("error", Map.of("message", "会话不存在: " + conversationId)));
            }

            // 2. 存用户消息
            MessageEntity userMsg = MessageEntity.builder()
                    .conversationId(conversationId)
                    .role("user")
                    .content(userContent)
                    .createdAt(LocalDateTime.now())
                    .build();
            messageMapper.insert(userMsg);

            // 3. 解析数据集 + 历史记忆 + 思考开关
            Long datasetId = request.getDatasetId() != null ? request.getDatasetId() : conversation.getDatasetId();
            boolean enableThinking = request.getEnableThinking() == null || request.getEnableThinking();
            List<MessageEntity> history = getRecentHistory(conversationId, 10);

            // 4. 构建上下文 + 路由智能体
            AgentContext ctx = new AgentContext(conversation, userContent, datasetId, enableThinking, history);
            Agent agent = agentRouter.route(ctx);
            log.info("[chatStream] 路由到智能体: {}, datasetId={}", agent.id(), datasetId);

            // 5. 智能体流式 + 收尾(done + 持久化) + 取消/异常处理
            Flux<ServerSentEvent<String>> doneEvent = Flux.defer(() -> persistAndDone(ctx, conversation, conversationId, t0));

            return Flux.concat(agent.stream(ctx), doneEvent)
                    .doOnCancel(() -> persistPartial(ctx, conversation, conversationId))
                    .onErrorResume(e -> {
                        log.error("[chatStream] 流式异常", e);
                        return Flux.just(SseEventBuilder.event("error",
                                Map.of("message", "生成回复失败: " + e.getMessage())));
                    });
        });
    }

    /** 流结束:保存 assistant 消息(含 thinking)、更新会话、发送 done 事件 */
    private Flux<ServerSentEvent<String>> persistAndDone(AgentContext ctx, ConversationEntity conversation,
                                                         Long conversationId, long t0) {
        MessageEntity assistantMsg = MessageEntity.builder()
                .conversationId(conversationId)
                .role("assistant")
                .content(ctx.getFullAnswer().toString())
                .thinking(ctx.getFullThinking().length() == 0 ? null : ctx.getFullThinking().toString())
                .createdAt(LocalDateTime.now())
                .build();
        messageMapper.insert(assistantMsg);

        int newCount = (conversation.getMessageCount() != null ? conversation.getMessageCount() : 0) + 2;
        conversation.setMessageCount(newCount);
        if (newCount <= 2 && "新对话".equals(conversation.getTitle())) {
            String autoTitle = ctx.getUserContent().length() > 30
                    ? ctx.getUserContent().substring(0, 30) + "..."
                    : ctx.getUserContent();
            conversation.setTitle(autoTitle);
        }
        conversationService.updateById(conversation);

        log.info("[chatStream] ========== 完成, 总耗时: {}ms, thinking.len={}, answer.len={}",
                System.currentTimeMillis() - t0, ctx.getFullThinking().length(), ctx.getFullAnswer().length());

        Map<String, Object> donePayload = new LinkedHashMap<>();
        donePayload.put("messageId", assistantMsg.getId());
        donePayload.put("conversationId", conversationId);
        donePayload.put("title", conversation.getTitle());
        if (ctx.getFullThinking().length() > 0) {
            donePayload.put("thinking", ctx.getFullThinking().toString());
        }
        return Flux.just(SseEventBuilder.raw(donePayload));
    }

    /** 用户取消/断开:保存已生成的部分内容 */
    private void persistPartial(AgentContext ctx, ConversationEntity conversation, Long conversationId) {
        if (ctx.getFullAnswer().length() == 0 && ctx.getFullThinking().length() == 0) {
            return;
        }
        log.info("[chatStream] 请求被取消, 保存部分内容, thinking.len={}, answer.len={}",
                ctx.getFullThinking().length(), ctx.getFullAnswer().length());
        try {
            MessageEntity partialMsg = MessageEntity.builder()
                    .conversationId(conversationId)
                    .role("assistant")
                    .content(ctx.getFullAnswer().toString())
                    .thinking(ctx.getFullThinking().length() == 0 ? null : ctx.getFullThinking().toString())
                    .createdAt(LocalDateTime.now())
                    .build();
            messageMapper.insert(partialMsg);
            int newCount = (conversation.getMessageCount() != null ? conversation.getMessageCount() : 0) + 1;
            conversation.setMessageCount(newCount);
            conversationService.updateById(conversation);
        } catch (Exception ex) {
            log.error("[chatStream] 保存取消时的部分内容失败", ex);
        }
    }

    /** 取最近 maxPairs 轮历史(按时间正序返回) */
    private List<MessageEntity> getRecentHistory(Long conversationId, int maxPairs) {
        List<MessageEntity> all = messageMapper.selectList(
                new LambdaQueryWrapper<MessageEntity>()
                        .eq(MessageEntity::getConversationId, conversationId)
                        .orderByDesc(MessageEntity::getCreatedAt));
        int limit = maxPairs * 2;
        List<MessageEntity> recent = all.size() > limit ? all.subList(0, limit) : all;
        Collections.reverse(recent);
        return recent;
    }
}
