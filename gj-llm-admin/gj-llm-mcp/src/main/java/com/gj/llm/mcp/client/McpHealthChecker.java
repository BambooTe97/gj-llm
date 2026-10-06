package com.gj.llm.mcp.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gj.llm.mcp.constant.McpConstants;
import com.gj.llm.mcp.entity.McpServerConfigEntity;
import com.gj.llm.mcp.mapper.McpServerConfigMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部 server 健康检查器 -- 定时 ping 所有启用连接，DOWN 尝试重连一次。
 *
 * <p>失败隔离：单连接故障只落自身 health_status，不上抛、不影响其他连接与主链路。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpHealthChecker {

    private final McpConnectionManager connectionManager;
    private final McpServerConfigMapper configMapper;

    @Scheduled(fixedDelayString = "${gj.llm.mcp.client.health-check-interval-ms:60000}",
            initialDelayString = "${gj.llm.mcp.client.health-check-initial-delay-ms:20000}")
    public void check() {
        List<McpServerConfigEntity> enabledServers = configMapper.selectList(
                new LambdaQueryWrapper<McpServerConfigEntity>()
                        .eq(McpServerConfigEntity::getEnabled, McpConstants.ENABLED));
        for (McpServerConfigEntity cfg : enabledServers) {
            try {
                McpSyncClient client = connectionManager.ensureConnected(cfg);
                if (connectionManager.ping(client)) {
                    markHealth(cfg.getId(), McpConstants.HEALTH_UP);
                } else {
                    // ping 失败：重连一次，成功即恢复
                    connectionManager.connect(cfg);
                    markHealth(cfg.getId(), McpConstants.HEALTH_UP);
                }
            } catch (Exception e) {
                markHealth(cfg.getId(), McpConstants.HEALTH_DOWN);
                log.warn("[MCP健康检查] {} 不可用: {}", cfg.getName(), e.getMessage());
            }
        }
    }

    private void markHealth(Long id, String status) {
        McpServerConfigEntity patch = new McpServerConfigEntity();
        patch.setId(id);
        patch.setHealthStatus(status);
        if (McpConstants.HEALTH_UP.equals(status)) {
            patch.setLastHealthyAt(LocalDateTime.now());
        }
        configMapper.updateById(patch);
    }
}
