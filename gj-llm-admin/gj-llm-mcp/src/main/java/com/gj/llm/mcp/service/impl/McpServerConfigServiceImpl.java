package com.gj.llm.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gj.llm.common.util.StringUtils;
import com.gj.llm.mcp.client.McpConnectionManager;
import com.gj.llm.mcp.client.McpToolRegistry;
import com.gj.llm.mcp.common.AesGcmTextCipher;
import com.gj.llm.mcp.constant.McpConstants;
import com.gj.llm.mcp.entity.McpServerConfigEntity;
import com.gj.llm.mcp.mapper.McpServerConfigMapper;
import com.gj.llm.mcp.model.ConnectionTestResult;
import com.gj.llm.mcp.model.McpServerConfigRequest;
import com.gj.llm.mcp.model.McpServerConfigVO;
import com.gj.llm.mcp.model.McpToolVO;
import com.gj.llm.mcp.service.McpServerConfigService;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 外部 MCP Server 配置服务实现 -- 保存前加密凭据，启停/保存即时驱动连接管理器。
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerConfigServiceImpl implements McpServerConfigService {

    private final McpServerConfigMapper configMapper;
    private final McpConnectionManager connectionManager;
    private final McpToolRegistry toolRegistry;
    private final AesGcmTextCipher cipher;

    @Override
    public McpServerConfigVO create(McpServerConfigRequest request) {
        McpServerConfigEntity entity = McpServerConfigEntity.builder()
                .name(requireName(request.getName()))
                .transport(normalizeTransport(request.getTransport()))
                .endpoint(requireEndpoint(request.getEndpoint()))
                .authHeaderName(trimToNull(request.getAuthHeaderName()))
                .authHeaderValue(encryptIfPresent(request.getAuthHeaderValue()))
                .enabled(request.getEnabled() == null || request.getEnabled()
                        ? McpConstants.ENABLED : McpConstants.DISABLED)
                .healthStatus(McpConstants.HEALTH_UNKNOWN)
                .toolCount(0)
                .remark(trimToNull(request.getRemark()))
                .build();
        configMapper.insert(entity);

        if (entity.getEnabled() == McpConstants.ENABLED) {
            connectAndRefresh(entity);
        }
        toolRegistry.invalidate(entity.getId());
        return toVO(selectById(entity.getId()));
    }

    @Override
    public McpServerConfigVO update(Long id, McpServerConfigRequest request) {
        McpServerConfigEntity old = selectById(id);
        McpServerConfigEntity patch = new McpServerConfigEntity();
        patch.setId(id);
        patch.setName(request.getName() != null ? requireName(request.getName()) : old.getName());
        patch.setTransport(request.getTransport() != null ? normalizeTransport(request.getTransport()) : old.getTransport());
        patch.setEndpoint(request.getEndpoint() != null ? requireEndpoint(request.getEndpoint()) : old.getEndpoint());
        patch.setAuthHeaderName(request.getAuthHeaderName() != null ? trimToNull(request.getAuthHeaderName()) : old.getAuthHeaderName());
        // 掩码/空 = 保留原密文，避免回显泄露
        patch.setAuthHeaderValue(encryptIfPresent(request.getAuthHeaderValue()) != null
                ? encryptIfPresent(request.getAuthHeaderValue()) : old.getAuthHeaderValue());
        patch.setRemark(request.getRemark() != null ? trimToNull(request.getRemark()) : old.getRemark());
        patch.setEnabled(old.getEnabled());
        if (configMapper.updateById(patch) <= 0) {
            throw new IllegalStateException("外部服务不存在: " + id);
        }

        // 配置变更即重建连接（旧连接作废）
        connectionManager.disconnect(id);
        toolRegistry.invalidate(id);
        McpServerConfigEntity latest = selectById(id);
        if (latest.getEnabled() == McpConstants.ENABLED) {
            connectAndRefresh(latest);
        }
        return toVO(selectById(id));
    }

    @Override
    public IPage<McpServerConfigVO> page(int page, int pageSize) {
        IPage<McpServerConfigEntity> result = configMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<McpServerConfigEntity>().orderByDesc(McpServerConfigEntity::getCreatedAt));
        return result.convert(this::toVO);
    }

    @Override
    public void updateEnabled(Long id, boolean enabled) {
        selectById(id);
        McpServerConfigEntity patch = new McpServerConfigEntity();
        patch.setId(id);
        patch.setEnabled(enabled ? McpConstants.ENABLED : McpConstants.DISABLED);
        configMapper.updateById(patch);

        toolRegistry.invalidate(id);
        if (enabled) {
            connectAndRefresh(selectById(id));
        } else {
            connectionManager.disconnect(id);
            markHealth(id, McpConstants.HEALTH_UNKNOWN, null);
        }
    }

    @Override
    public void delete(Long id) {
        selectById(id);
        connectionManager.disconnect(id);
        toolRegistry.invalidate(id);
        configMapper.deleteById(id);
    }

    @Override
    public ConnectionTestResult testConnect(Long id) {
        McpServerConfigEntity cfg = selectById(id);
        try {
            McpSyncClient client = connectionManager.connect(cfg);
            int toolCount = client.listTools().tools().size();
            markHealth(id, McpConstants.HEALTH_UP, toolCount);
            // 停用状态的 server 仅测试不常驻
            if (cfg.getEnabled() == null || cfg.getEnabled() != McpConstants.ENABLED) {
                connectionManager.disconnect(id);
            }
            toolRegistry.invalidate(id);
            return new ConnectionTestResult(true, "连接成功，发现 " + toolCount + " 个工具", toolCount);
        } catch (Exception e) {
            markHealth(id, McpConstants.HEALTH_DOWN, null);
            return new ConnectionTestResult(false, "连接失败: " + e.getMessage(), 0);
        }
    }

    @Override
    public List<McpToolVO> listTools(Long id) {
        McpServerConfigEntity cfg = selectById(id);
        McpSyncClient client = connectionManager.connected(id)
                .orElseGet(() -> cfg.getEnabled() == McpConstants.ENABLED ? connectionManager.connect(cfg) : null);
        if (client == null) {
            throw new IllegalStateException("服务未启用且未连接，请先启用或测试连接: " + cfg.getName());
        }
        return client.listTools().tools().stream()
                .map(tool -> new McpToolVO(tool.name(), tool.description()))
                .toList();
    }

    /** 建连并回写健康状态与工具数 */
    private void connectAndRefresh(McpServerConfigEntity cfg) {
        try {
            McpSyncClient client = connectionManager.connect(cfg);
            int toolCount = client.listTools().tools().size();
            markHealth(cfg.getId(), McpConstants.HEALTH_UP, toolCount);
        } catch (Exception e) {
            markHealth(cfg.getId(), McpConstants.HEALTH_DOWN, null);
            log.warn("[MCP-SERVER] 启用后建连失败: {}: {}", cfg.getName(), e.getMessage());
        }
    }

    private void markHealth(Long id, String status, Integer toolCount) {
        McpServerConfigEntity patch = new McpServerConfigEntity();
        patch.setId(id);
        patch.setHealthStatus(status);
        patch.setToolCount(toolCount);
        if (McpConstants.HEALTH_UP.equals(status)) {
            patch.setLastHealthyAt(LocalDateTime.now());
        }
        configMapper.updateById(patch);
    }

    private McpServerConfigEntity selectById(Long id) {
        McpServerConfigEntity entity = configMapper.selectById(id);
        if (entity == null) {
            throw new IllegalStateException("外部服务不存在: " + id);
        }
        return entity;
    }

    private String requireName(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            throw new IllegalArgumentException("连接标识不能为空");
        }
        return trimmed;
    }

    private String requireEndpoint(String endpoint) {
        String trimmed = trimToNull(endpoint);
        if (trimmed == null) {
            throw new IllegalArgumentException("端点 URL 不能为空");
        }
        return trimmed;
    }

    private String normalizeTransport(String transport) {
        String trimmed = trimToNull(transport);
        if (trimmed == null) {
            return McpConstants.TRANSPORT_STREAMABLE_HTTP;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT).replace("-", "_");
        if (McpConstants.TRANSPORT_STREAMABLE_HTTP.equals(upper) || McpConstants.TRANSPORT_SSE.equals(upper)) {
            return upper;
        }
        throw new IllegalArgumentException("不支持的传输类型: " + transport + "（可选 STREAMABLE_HTTP / SSE）");
    }

    private String encryptIfPresent(String plain) {
        String trimmed = trimToNull(plain);
        if (trimmed == null || McpServerConfigRequest.MASK.equals(trimmed)) {
            return null;
        }
        return cipher.encrypt(trimmed);
    }

    private String trimToNull(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private McpServerConfigVO toVO(McpServerConfigEntity entity) {
        return McpServerConfigVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .transport(entity.getTransport())
                .endpoint(entity.getEndpoint())
                .authHeaderName(entity.getAuthHeaderName())
                .hasAuth(entity.getAuthHeaderValue() != null && !entity.getAuthHeaderValue().isBlank())
                .enabled(entity.getEnabled())
                .healthStatus(entity.getHealthStatus())
                .lastHealthyAt(entity.getLastHealthyAt())
                .toolCount(entity.getToolCount())
                .remark(entity.getRemark())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
