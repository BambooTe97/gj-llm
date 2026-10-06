package com.gj.llm.mcp.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部 MCP Server 配置视图对象 -- 不回显认证凭据（仅 hasAuth 标记）。
 *
 * @author gj-llm
 */
@Data
@Builder
public class McpServerConfigVO {

    private Long id;

    private String name;

    /** 传输类型：STREAMABLE_HTTP / SSE */
    private String transport;

    private String endpoint;

    private String authHeaderName;

    /** 是否已配置认证凭据 */
    private Boolean hasAuth;

    private Integer enabled;

    /** UP / DOWN / UNKNOWN */
    private String healthStatus;

    private LocalDateTime lastHealthyAt;

    private Integer toolCount;

    private String remark;

    private LocalDateTime createdAt;
}
