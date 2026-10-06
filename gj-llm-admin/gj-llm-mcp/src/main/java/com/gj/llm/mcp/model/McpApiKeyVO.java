package com.gj.llm.mcp.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 视图对象 -- 不含 keyHash；fullKey 仅发放响应中一次性返回。
 *
 * @author gj-llm
 */
@Data
@Builder
public class McpApiKeyVO {

    private Long id;

    private Long userId;

    private String username;

    private String name;

    /** 前缀明文（辨识用） */
    private String keyPrefix;

    /** 1=启用 0=停用 */
    private Integer status;

    private LocalDateTime expiresAt;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createdAt;

    /** 完整明文 key —— 仅发放响应携带（库内不存，丢失只能吊销重发） */
    private String fullKey;
}
