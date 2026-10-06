package com.gj.llm.mcp.event;

/**
 * MCP 工具调用事件 -- 两端装饰器发布，监听器异步落库（主链路零阻塞）。
 *
 * @param direction     调用方向：SERVER_CALLED / CLIENT_CALL
 * @param userId        调用方用户 ID
 * @param username      调用方用户名（快照）
 * @param apiKeyId      server 被调时的 API Key ID
 * @param serverName    client 出调时的目标 server 名
 * @param toolName      工具名
 * @param paramsDigest  入参摘要（超长截断）
 * @param resultStatus  结果状态：SUCCESS / ERROR / TIMEOUT
 * @param errorMessage  失败原因
 * @param costMs        耗时（毫秒）
 * @param clientIp      来源 IP
 * @author gj-llm
 */
public record McpToolCallEvent(String direction,
                               Long userId,
                               String username,
                               Long apiKeyId,
                               String serverName,
                               String toolName,
                               String paramsDigest,
                               String resultStatus,
                               String errorMessage,
                               long costMs,
                               String clientIp) {

    /** server 被调事件 */
    public static McpToolCallEvent serverCalled(Long userId, String username, Long apiKeyId,
                                                String toolName, String paramsDigest,
                                                String resultStatus, String errorMessage,
                                                long costMs, String clientIp) {
        return new McpToolCallEvent("SERVER_CALLED", userId, username, apiKeyId,
                null, toolName, paramsDigest, resultStatus, errorMessage, costMs, clientIp);
    }

    /** client 出调事件 */
    public static McpToolCallEvent clientCalled(Long userId, String username, String serverName,
                                                String toolName, String paramsDigest,
                                                String resultStatus, String errorMessage, long costMs) {
        return new McpToolCallEvent("CLIENT_CALL", userId, username, null,
                serverName, toolName, paramsDigest, resultStatus, errorMessage, costMs, null);
    }
}
