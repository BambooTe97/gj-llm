package com.gj.llm.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gj.llm.common.util.SecurityUtils;
import com.gj.llm.common.util.StringUtils;
import com.gj.llm.mcp.constant.McpConstants;
import com.gj.llm.mcp.entity.McpApiKeyEntity;
import com.gj.llm.mcp.mapper.McpApiKeyMapper;
import com.gj.llm.mcp.model.McpApiKeyCreateRequest;
import com.gj.llm.mcp.model.McpApiKeyVO;
import com.gj.llm.mcp.service.McpApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * MCP API Key 服务实现。
 *
 * <p>密钥形态：{@code mcp_} + 43 位 Base64URL 随机串；库内只存 SHA-256 哈希，
 * 明文仅发放响应一次性返回，丢失只能吊销重发。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpApiKeyServiceImpl implements McpApiKeyService {

    /** 随机字节数（32 字节 -> 43 位 Base64URL） */
    private static final int RANDOM_BYTES = 32;
    /** 前缀明文长度（mcp_ + 7 位，辨识用） */
    private static final int PREFIX_VISIBLE_LENGTH = 12;

    private final McpApiKeyMapper apiKeyMapper;
    private final SecureRandom random = new SecureRandom();

    @Override
    public McpApiKeyVO create(McpApiKeyCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("未登录用户不能发放 API Key");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Key 用途名称不能为空");
        }

        String rawKey = McpConstants.KEY_PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
        McpApiKeyEntity entity = McpApiKeyEntity.builder()
                .userId(userId)
                .username(SecurityUtils.getCurrentUsername())
                .name(request.getName().trim())
                .keyPrefix(rawKey.substring(0, PREFIX_VISIBLE_LENGTH))
                .keyHash(sha256Hex(rawKey))
                .status(McpConstants.ENABLED)
                .expiresAt(request.getExpiresInDays() == null
                        ? null
                        : LocalDateTime.now().plusDays(request.getExpiresInDays()))
                .build();
        apiKeyMapper.insert(entity);
        log.info("[MCP-KEY] 发放 API Key: id={}, user={}, name={}", entity.getId(), entity.getUsername(), entity.getName());
        return toVO(entity, rawKey);
    }

    @Override
    public IPage<McpApiKeyVO> page(int page, int pageSize) {
        IPage<McpApiKeyEntity> result = apiKeyMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<McpApiKeyEntity>().orderByDesc(McpApiKeyEntity::getCreatedAt));
        return result.convert(e -> toVO(e, null));
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != McpConstants.ENABLED && status != McpConstants.DISABLED)) {
            throw new IllegalArgumentException("非法的状态值: " + status);
        }
        McpApiKeyEntity patch = new McpApiKeyEntity();
        patch.setId(id);
        patch.setStatus(status);
        if (apiKeyMapper.updateById(patch) <= 0) {
            throw new IllegalStateException("API Key 不存在: " + id);
        }
    }

    @Override
    public void delete(Long id) {
        if (apiKeyMapper.deleteById(id) <= 0) {
            throw new IllegalStateException("API Key 不存在: " + id);
        }
    }

    @Override
    public McpApiKeyEntity validateByKey(String rawKey) {
        if (StringUtils.isBlank(rawKey)) {
            return null;
        }
        McpApiKeyEntity entity = apiKeyMapper.selectOne(
                new LambdaQueryWrapper<McpApiKeyEntity>().eq(McpApiKeyEntity::getKeyHash, sha256Hex(rawKey)));
        if (entity == null) {
            return null;
        }
        if (entity.getStatus() == null || entity.getStatus() != McpConstants.ENABLED) {
            return null;
        }
        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        // 触摸最近使用时间（失败不阻断认证）
        McpApiKeyEntity touch = new McpApiKeyEntity();
        touch.setId(entity.getId());
        touch.setLastUsedAt(LocalDateTime.now());
        try {
            apiKeyMapper.updateById(touch);
        } catch (Exception e) {
            log.warn("[MCP-KEY] 触摸 last_used_at 失败: {}", entity.getId());
        }
        return entity;
    }

    private byte[] randomBytes() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return bytes;
    }

    private String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    private McpApiKeyVO toVO(McpApiKeyEntity entity, String fullKey) {
        return McpApiKeyVO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .name(entity.getName())
                .keyPrefix(entity.getKeyPrefix())
                .status(entity.getStatus())
                .expiresAt(entity.getExpiresAt())
                .lastUsedAt(entity.getLastUsedAt())
                .createdAt(entity.getCreatedAt())
                .fullKey(fullKey)
                .build();
    }
}
