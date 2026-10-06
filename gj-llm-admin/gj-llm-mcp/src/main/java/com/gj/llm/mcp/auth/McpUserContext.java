package com.gj.llm.mcp.auth;

import lombok.Getter;
import org.springframework.ai.chat.model.ToolContext;

/**
 * MCP 调用方用户上下文（ThreadLocal）-- server 被调链路的身份载体。
 *
 * <p>API Key 拦截器校验通过后写入，工具执行时读取以做可见域隔离与审计，
 * 请求结束由拦截器 afterCompletion 清理，防线程池串号。</p>
 *
 * <p>chat 工具循环链路不走此上下文（工具可能在 reactor 线程执行，
 * ThreadLocal 不可靠），身份经 ToolContext 显式下传，见 {@link #resolveUserId}。</p>
 *
 * @author gj-llm
 */
public final class McpUserContext {

    private McpUserContext() {
    }

    /** MCP 调用方主体 */
    @Getter
    public static class McpPrincipal {
        private final Long userId;
        private final String username;
        private final Long apiKeyId;
        private final String clientIp;

        public McpPrincipal(Long userId, String username, Long apiKeyId, String clientIp) {
            this.userId = userId;
            this.username = username;
            this.apiKeyId = apiKeyId;
            this.clientIp = clientIp;
        }
    }

    private static final ThreadLocal<McpPrincipal> HOLDER = new ThreadLocal<>();

    public static void set(McpPrincipal principal) {
        HOLDER.set(principal);
    }

    /** 当前调用方，server 被调链路非空；chat 链路/非 MCP 线程为 null */
    public static McpPrincipal get() {
        return HOLDER.get();
    }

    /** 当前调用方 userId，null 安全 */
    public static Long userIdOrNull() {
        McpPrincipal p = HOLDER.get();
        return p == null ? null : p.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 工具执行时的身份解析统一入口：优先 server 被调链路（ThreadLocal），
     * 兜底 chat 工具循环链路（ToolContext 显式下传的 userId，可能发生在 reactor 线程）。
     */
    public static Long resolveUserId(ToolContext toolContext) {
        Long userId = userIdOrNull();
        if (userId == null && toolContext != null && toolContext.getContext() != null) {
            Object value = toolContext.getContext().get("userId");
            if (value instanceof Long l) {
                return l;
            }
            if (value instanceof Number n) {
                return n.longValue();
            }
        }
        return userId;
    }
}
