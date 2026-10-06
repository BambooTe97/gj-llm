package com.gj.llm.mcp.model;

import lombok.Data;

/**
 * 外部 MCP Server 配置保存请求（新增/更新共用）。
 *
 * <p>authHeaderValue 传掩码（******）或空表示保留原值，避免密文回显泄露。</p>
 *
 * @author gj-llm
 */
@Data
public class McpServerConfigRequest {

    /** 连接标识（唯一） */
    private String name;

    /** 传输类型：STREAMABLE_HTTP / SSE */
    private String transport;

    /** 完整 URL（如 http://host:port/mcp） */
    private String endpoint;

    /** 认证头名（如 Authorization），可空 */
    private String authHeaderName;

    /** 认证头值（明文提交，落库前加密；掩码/空=保留原值） */
    private String authHeaderValue;

    /** 是否启用（保存后立即生效） */
    private Boolean enabled;

    /** 备注 */
    private String remark;

    /** 掩码标记 */
    public static final String MASK = "******";
}
