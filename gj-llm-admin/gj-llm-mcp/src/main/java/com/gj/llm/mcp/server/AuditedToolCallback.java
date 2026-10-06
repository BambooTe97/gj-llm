package com.gj.llm.mcp.server;

import com.gj.llm.mcp.auth.McpUserContext;
import com.gj.llm.mcp.auth.McpUserContext.McpPrincipal;
import com.gj.llm.mcp.constant.McpConstants;
import com.gj.llm.mcp.event.McpToolCallEvent;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.TimeoutException;

/**
 * Server 被调审计装饰器 -- MCP Server 暴露的每个工具包一层，
 * 执行前后发布 SERVER_CALLED 审计事件（异步落库），异常原样上抛由
 * MCP 协议层返回错误给客户端。
 *
 * @author gj-llm
 */
public class AuditedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ApplicationEventPublisher publisher;
    private final int paramsMaxLength;

    public AuditedToolCallback(ToolCallback delegate, ApplicationEventPublisher publisher, int paramsMaxLength) {
        this.delegate = delegate;
        this.publisher = publisher;
        this.paramsMaxLength = paramsMaxLength;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return auditCall(toolInput, input -> delegate.call(input));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return auditCall(toolInput, input -> delegate.call(input, toolContext));
    }

    private String auditCall(String toolInput, ToolInvocation invocation) {
        String toolName = delegate.getToolDefinition().name();
        long start = System.currentTimeMillis();
        McpPrincipal principal = McpUserContext.get();
        String status = McpConstants.STATUS_ERROR;
        String errorMessage = null;
        try {
            String result = invocation.invoke(toolInput);
            status = McpConstants.STATUS_SUCCESS;
            return result;
        } catch (Exception e) {
            status = e instanceof TimeoutException ? McpConstants.STATUS_TIMEOUT : McpConstants.STATUS_ERROR;
            errorMessage = e.getMessage() == null ? e.getClass().getSimpleName()
                    : abbreviate(e.getMessage(), 400);
            // ToolCallback 契约不声明受检异常:受检异常包装上抛,由协议层返回错误给客户端
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e);
        } finally {
            long costMs = System.currentTimeMillis() - start;
            publisher.publishEvent(McpToolCallEvent.serverCalled(
                    principal == null ? null : principal.getUserId(),
                    principal == null ? null : principal.getUsername(),
                    principal == null ? null : principal.getApiKeyId(),
                    toolName, abbreviate(toolInput, paramsMaxLength), status, errorMessage, costMs,
                    principal == null ? null : principal.getClientIp()));
        }
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    @FunctionalInterface
    private interface ToolInvocation {
        String invoke(String input) throws Exception;
    }
}
