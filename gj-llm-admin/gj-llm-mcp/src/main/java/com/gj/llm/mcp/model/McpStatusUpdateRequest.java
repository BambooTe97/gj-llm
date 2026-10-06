package com.gj.llm.mcp.model;

import lombok.Data;

/**
 * 状态更新请求（API Key 启停）。
 *
 * @author gj-llm
 */
@Data
public class McpStatusUpdateRequest {

    /** 1=启用 0=停用 */
    private Integer status;
}
