package com.gj.llm.mcp.client;

import com.gj.llm.mcp.constant.McpConstants;
import com.gj.llm.mcp.event.McpToolCallEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 工具调用出调装饰器（Spy）-- chat 工具循环链路逐请求包装：
 * ① 发 tool_call/tool_result 旁路事件（经 {@link ToolCallListener}，可空）；
 * ② 发布 CLIENT_CALL 审计事件（异步落库）；
 * ③ 降级兜底：工具抛错不向模型链路上抛，改为返回错误说明 JSON，对话继续生成。
 *
 * <p>超时由底层保证（外部工具走 MCP 客户端 requestTimeout，内置检索走检索管线自身超时），
 * 此处仅按异常形态归类 TIMEOUT/ERROR。</p>
 *
 * @author gj-llm
 */
@Slf4j
public class ToolCallSpyCallback implements ToolCallback {

    /** SSE 事件里的入参/结果预览上限 */
    private static final int EVENT_PREVIEW_MAX = 160;

    private final ToolCallback delegate;
    private final String serverName;
    private final Long userId;
    private final String username;
    private final ToolCallListener listener;
    private final ApplicationEventPublisher publisher;
    private final int auditParamsMaxLength;

    public ToolCallSpyCallback(ToolCallback delegate, String serverName, Long userId, String username,
                               ToolCallListener listener, ApplicationEventPublisher publisher,
                               int auditParamsMaxLength) {
        this.delegate = delegate;
        this.serverName = serverName;
        this.userId = userId;
        this.username = username;
        this.listener = listener;
        this.publisher = publisher;
        this.auditParamsMaxLength = auditParamsMaxLength;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return spyCall(toolInput, input -> delegate.call(input));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return spyCall(toolInput, input -> delegate.call(input, toolContext));
    }

    /** 统一包装：事件 + 审计 + 降级 */
    private String spyCall(String toolInput, ToolInvocation invocation) {
        String toolName = delegate.getToolDefinition().name();
        long start = System.currentTimeMillis();
        if (listener != null) {
            listener.onCall(toolName, preview(toolInput, EVENT_PREVIEW_MAX));
        }
        String result;
        String status;
        String errorMessage = null;
        try {
            result = invocation.invoke(toolInput);
            status = McpConstants.STATUS_SUCCESS;
        } catch (Exception e) {
            status = isTimeout(e) ? McpConstants.STATUS_TIMEOUT : McpConstants.STATUS_ERROR;
            errorMessage = abbreviate(e.getMessage(), 400);
            result = "{\"error\":\"工具调用失败: " + errorMessage + "\"}";
            log.warn("[MCP工具] 调用失败: tool={}, server={}, status={}", toolName, serverName, status);
        }
        long costMs = System.currentTimeMillis() - start;
        if (listener != null) {
            listener.onResult(toolName, McpConstants.STATUS_SUCCESS.equals(status),
                    preview(result, EVENT_PREVIEW_MAX), costMs, errorMessage);
        }
        publisher.publishEvent(McpToolCallEvent.clientCalled(userId, username, serverName,
                toolName, preview(toolInput, auditParamsMaxLength), status, errorMessage, costMs));
        return result;
    }

    /** 超时归类：JDK 超时异常或异常链中带 timeout 语义 */
    private boolean isTimeout(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof java.util.concurrent.TimeoutException
                    || cur instanceof io.modelcontextprotocol.spec.McpError && isTimeoutMessage(cur.getMessage())) {
                return true;
            }
            cur = cur.getCause();
        }
        return isTimeoutMessage(e.getMessage());
    }

    private boolean isTimeoutMessage(String msg) {
        return msg != null && msg.toLowerCase().contains("timeout");
    }

    private String preview(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 工具调用函数式接口（区分是否带 ToolContext 的委托路径） */
    @FunctionalInterface
    private interface ToolInvocation {
        String invoke(String input) throws Exception;
    }
}
