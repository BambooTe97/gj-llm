package com.gj.llm.mcp.model;

import lombok.Data;

/**
 * API Key 发放请求。
 *
 * @author gj-llm
 */
@Data
public class McpApiKeyCreateRequest {

    /** Key 用途名称（必填） */
    private String name;

    /** 有效天数（null=永不过期） */
    private Integer expiresInDays;
}
