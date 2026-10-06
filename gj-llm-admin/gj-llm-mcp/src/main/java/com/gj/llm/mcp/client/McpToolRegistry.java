package com.gj.llm.mcp.client;

import com.gj.llm.mcp.config.McpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部工具注册表 -- 聚合所有「已启用且已连接」server 的工具为 Spring AI ToolCallback。
 *
 * <p>按 serverId 缓存 {@link SyncMcpToolCallbackProvider}（其内部缓存工具清单，
 * 避免每次对话请求都发 listTools HTTP）；配置/连接变更时由管理服务与健康检查器
 * 调 {@link #invalidate(Long)} 失效对应缓存。chat 侧拿到的是裸回调，
 * 身份与 SSE 事件由 ToolCallAgent 逐请求包 {@link ToolCallSpyCallback} 注入。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolRegistry {

    private final McpConnectionManager connectionManager;
    private final McpProperties properties;

    /** serverId -> 工具回调 Provider 缓存 */
    private final Map<Long, SyncMcpToolCallbackProvider> providers = new ConcurrentHashMap<>();

    /** 每个 server 的工具回调（带 server 名，供装饰器标注审计归属） */
    public record ServerToolCallbacks(String serverName, List<ToolCallback> callbacks) {
    }

    /** 聚合所有可用外部工具；全局开关关闭或无可用连接时返回空 */
    public List<ServerToolCallbacks> externalCallbacks() {
        if (!properties.getClient().isEnabled()) {
            return List.of();
        }
        List<ServerToolCallbacks> out = new ArrayList<>();
        for (McpConnectionManager.EnabledConnection conn : connectionManager.connectedEnabled()) {
            try {
                SyncMcpToolCallbackProvider provider = providers.computeIfAbsent(
                        conn.config().getId(), id -> new SyncMcpToolCallbackProvider(List.of(conn.client())));
                out.add(new ServerToolCallbacks(conn.config().getName(),
                        List.of(provider.getToolCallbacks())));
            } catch (Exception e) {
                // 失败隔离：单 server 工具发现失败不影响其他 server 与主对话
                log.warn("[MCP工具] 工具发现失败, server={}: {}", conn.config().getName(), e.getMessage());
                providers.remove(conn.config().getId());
            }
        }
        return out;
    }

    /** 失效指定 server 的工具缓存（配置变更/重连后调用） */
    public void invalidate(Long serverId) {
        providers.remove(serverId);
    }

    /** 失效全部缓存 */
    public void invalidateAll() {
        providers.clear();
    }
}
