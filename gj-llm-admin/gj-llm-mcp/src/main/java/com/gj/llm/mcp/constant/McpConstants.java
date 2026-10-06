package com.gj.llm.mcp.constant;

/**
 * MCP 模块常量 -- 方向 / 结果状态 / 传输类型 / 健康状态 / 默认值。
 *
 * <p>常量统一放接口（模块内静态常量类），避免散落在各实现类。</p>
 *
 * @author gj-llm
 */
public final class McpConstants {

    private McpConstants() {
    }

    /** 审计：调用方向 */
    public static final String DIRECTION_SERVER_CALLED = "SERVER_CALLED";
    public static final String DIRECTION_CLIENT_CALL = "CLIENT_CALL";

    /** 审计：结果状态 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_TIMEOUT = "TIMEOUT";

    /** 外部 server 传输类型 */
    public static final String TRANSPORT_STREAMABLE_HTTP = "STREAMABLE_HTTP";
    public static final String TRANSPORT_SSE = "SSE";

    /** 外部 server 健康状态 */
    public static final String HEALTH_UP = "UP";
    public static final String HEALTH_DOWN = "DOWN";
    public static final String HEALTH_UNKNOWN = "UNKNOWN";

    /** API Key 前缀（发放的明文 key 形如 mcp_xxxxxx...） */
    public static final String KEY_PREFIX = "mcp_";

    /** 停用/启用状态（与 mcp_api_key.status、mcp_server_config.enabled 对应） */
    public static final int ENABLED = 1;
    public static final int DISABLED = 0;

    /** 工具结果内容截断长度（search_knowledge 返回给模型的单条引用正文上限） */
    public static final int TOOL_CONTENT_MAX_CHARS = 400;
}
