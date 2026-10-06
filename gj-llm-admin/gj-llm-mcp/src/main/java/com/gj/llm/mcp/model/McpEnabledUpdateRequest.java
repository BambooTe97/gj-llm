package com.gj.llm.mcp.model;

import lombok.Data;

/**
 * 启停更新请求（外部 server）。
 *
 * @author gj-llm
 */
@Data
public class McpEnabledUpdateRequest {

    /** true=启用（立即建连） false=停用（立即断连） */
    private Boolean enabled;
}
