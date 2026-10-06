package com.gj.llm.mcp.client;

import com.gj.llm.mcp.common.AesGcmTextCipher;
import com.gj.llm.mcp.config.McpProperties;
import com.gj.llm.mcp.constant.McpConstants;
import com.gj.llm.mcp.entity.McpServerConfigEntity;
import com.gj.llm.mcp.mapper.McpServerConfigMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 连接管理器 -- 自建生命周期管理（不走 starter 的一次性自动装配）。
 *
 * <p>按 {@code mcp_server_config} 驱动：connect/disconnect/restart/ping，运行时启停即时生效。
 * 只管连接生命周期，不写库（健康状态/工具数由健康检查器与管理服务回写）。
 * 管理操作串行化（synchronized），调用频次低，无并发压力。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpConnectionManager {

    private static final String CLIENT_NAME = "gj-llm-mcp-client";
    private static final String CLIENT_VERSION = "1.0.0";
    private static final String DEFAULT_MCP_PATH = "/mcp";

    private final McpProperties properties;
    private final McpServerConfigMapper configMapper;
    private final AesGcmTextCipher cipher;

    /** serverId -> 已建立连接的客户端 */
    private final Map<Long, McpSyncClient> clients = new ConcurrentHashMap<>();

    /** 已启用且已连接的连接（chat 工具聚合用，不主动建连） */
    public record EnabledConnection(McpServerConfigEntity config, McpSyncClient client) {
    }

    /** 建（重）连：先断旧连，成功后入池；失败抛出并保证不留半连接 */
    public synchronized McpSyncClient connect(McpServerConfigEntity cfg) {
        disconnect(cfg.getId());
        McpSyncClient client = buildClient(cfg);
        try {
            client.initialize();
            clients.put(cfg.getId(), client);
            log.info("[MCP连接] 已连接: {} -> {}", cfg.getName(), cfg.getEndpoint());
            return client;
        } catch (Exception e) {
            closeQuietly(client, cfg.getName());
            throw new IllegalStateException("MCP 连接失败: " + cfg.getName() + " - " + e.getMessage(), e);
        }
    }

    /** 断开并移除（幂等） */
    public synchronized void disconnect(Long serverId) {
        McpSyncClient client = clients.remove(serverId);
        if (client != null) {
            closeQuietly(client, String.valueOf(serverId));
        }
    }

    /** 已连接的客户端（chat 工具聚合路径，绝不主动建连避免阻塞对话） */
    public Optional<McpSyncClient> connected(Long serverId) {
        return Optional.ofNullable(clients.get(serverId));
    }

    /** 已连接则取，未连则建（健康检查/启用/测试路径） */
    public synchronized McpSyncClient ensureConnected(McpServerConfigEntity cfg) {
        McpSyncClient existing = clients.get(cfg.getId());
        if (existing != null) {
            return existing;
        }
        return connect(cfg);
    }

    /** 已启用且已连接的连接列表 */
    public List<EnabledConnection> connectedEnabled() {
        List<EnabledConnection> out = new ArrayList<>();
        for (McpServerConfigEntity cfg : configMapper.selectList(null)) {
            if (cfg.getEnabled() == null || cfg.getEnabled() != McpConstants.ENABLED) {
                continue;
            }
            McpSyncClient client = clients.get(cfg.getId());
            if (client != null) {
                out.add(new EnabledConnection(cfg, client));
            }
        }
        return out;
    }

    /** 健康探测：返回是否存活 */
    public boolean ping(McpSyncClient client) {
        try {
            client.ping();
            return true;
        } catch (Exception e) {
            log.debug("[MCP连接] ping 失败: {}", e.getMessage());
            return false;
        }
    }

    /** 按配置构建传输层与同步客户端（未 initialize） */
    private McpSyncClient buildClient(McpServerConfigEntity cfg) {
        URI uri = URI.create(cfg.getEndpoint());
        String base = uri.getScheme() + "://" + uri.getRawAuthority();
        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) ? DEFAULT_MCP_PATH : uri.getRawPath();

        McpClientTransport transport;
        if (McpConstants.TRANSPORT_SSE.equalsIgnoreCase(cfg.getTransport())) {
            HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(base)
                    .sseEndpoint(path)
                    .connectTimeout(Duration.ofMillis(properties.getClient().getInitTimeoutMs()));
            applyAuth(builder::httpRequestCustomizer, cfg);
            transport = builder.build();
        } else {
            HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport.builder(base)
                    .endpoint(path)
                    .connectTimeout(Duration.ofMillis(properties.getClient().getInitTimeoutMs()));
            applyAuth(builder::httpRequestCustomizer, cfg);
            transport = builder.build();
        }

        return McpClient.sync(transport)
                .requestTimeout(Duration.ofMillis(properties.getClient().getRequestTimeoutMs()))
                .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
                .build();
    }

    /** 认证头注入：解密后经请求定制器加到每个出站请求 */
    private void applyAuth(java.util.function.Consumer<McpSyncHttpClientRequestCustomizer> setter,
                           McpServerConfigEntity cfg) {
        if (cfg.getAuthHeaderName() == null || cfg.getAuthHeaderName().isBlank()) {
            return;
        }
        String headerName = cfg.getAuthHeaderName();
        String headerValue = cipher.decrypt(cfg.getAuthHeaderValue());
        setter.accept((builder, method, uri, attrs, ctx) -> builder.header(headerName, headerValue));
    }

    private void closeQuietly(McpSyncClient client, String name) {
        try {
            client.closeGracefully();
        } catch (Exception e) {
            log.warn("[MCP连接] 关闭连接异常: {}", name, e);
        }
    }
}
