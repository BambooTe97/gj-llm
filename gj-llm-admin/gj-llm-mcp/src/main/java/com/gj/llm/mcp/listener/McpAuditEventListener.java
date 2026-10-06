package com.gj.llm.mcp.listener;

import com.gj.llm.mcp.entity.McpAuditLogEntity;
import com.gj.llm.mcp.event.McpToolCallEvent;
import com.gj.llm.mcp.mapper.McpAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MCP 审计事件监听器 -- @Async 异步落库，不阻塞工具调用主链路。
 *
 * <p>线程池复用 gj-common {@code AsyncThreadPoolConfig} 的 taskExecutor；
 * 审计失败只记 WARN 不上抛（审计是旁路，不能影响业务）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpAuditEventListener {

    private final McpAuditLogMapper auditLogMapper;

    @Async
    @EventListener
    public void onToolCall(McpToolCallEvent event) {
        try {
            McpAuditLogEntity entity = McpAuditLogEntity.builder()
                    .direction(event.direction())
                    .userId(event.userId())
                    .username(event.username())
                    .apiKeyId(event.apiKeyId())
                    .serverName(event.serverName())
                    .toolName(event.toolName())
                    .paramsDigest(event.paramsDigest())
                    .resultStatus(event.resultStatus())
                    .errorMessage(event.errorMessage())
                    .costMs(event.costMs())
                    .clientIp(event.clientIp())
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("[MCP审计] 落库失败: tool={}, direction={}", event.toolName(), event.direction(), e);
        }
    }
}
