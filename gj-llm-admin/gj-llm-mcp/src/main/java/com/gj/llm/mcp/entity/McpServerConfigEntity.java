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
 * 外部 MCP Server 连接配置实体 -- DB 驱动动态管理（CRUD/启停运行时生效）。
 *
 * <p>auth_header_value 为 AES-GCM 加密密文，接口回显一律掩码。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_server_config")
public class McpServerConfigEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 连接标识（唯一） */
    private String name;

    /** 传输类型：STREAMABLE_HTTP / SSE */
    private String transport;

    /** 完整 URL（如 http://host:port/mcp） */
    private String endpoint;

    /** 认证头名（如 Authorization） */
    private String authHeaderName;

    /** 认证头值（AES-GCM 加密存储） */
    private String authHeaderValue;

    /** 1=启用 0=停用（启停运行时生效） */
    private Integer enabled;

    /** 健康状态：UP / DOWN / UNKNOWN */
    private String healthStatus;

    /** 最近健康时间 */
    private LocalDateTime lastHealthyAt;

    /** 工具数量（最近一次发现） */
    private Integer toolCount;

    /** 备注 */
    private String remark;
}
