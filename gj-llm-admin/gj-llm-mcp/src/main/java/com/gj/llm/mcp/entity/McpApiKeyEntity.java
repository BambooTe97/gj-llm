package com.gj.llm.mcp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gj.llm.mybatis.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MCP API Key 实体 -- server 对外认证凭据。
 *
 * <p>明文 key 仅发放时返回一次，库内只存 SHA-256 哈希（key_hash 唯一索引）；
 * user_id 为可见域判定主体，username 为发放时快照（审计展示用）。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_api_key")
public class McpApiKeyEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 绑定用户 ID（可见域判定主体） */
    private Long userId;

    /** 用户名快照（审计展示用，免跨模块联查） */
    private String username;

    /** Key 用途名称 */
    private String name;

    /** Key 前缀明文（辨识用，如 mcp_Ab3dEf） */
    private String keyPrefix;

    /** SHA-256 哈希（唯一索引） */
    private String keyHash;

    /** 1=启用 0=停用 */
    private Integer status;

    /** 过期时间（null=永不过期） */
    private LocalDateTime expiresAt;

    /** 最近使用时间 */
    private LocalDateTime lastUsedAt;
}
