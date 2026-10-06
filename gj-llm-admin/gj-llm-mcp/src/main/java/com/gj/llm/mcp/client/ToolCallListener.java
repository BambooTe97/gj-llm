package com.gj.llm.mcp.client;

/**
 * 工具调用监听器 -- mcp 模块与 chat 模块解耦的旁路事件出口。
 *
 * <p>chat 侧实现为 SSE 事件发射（tool_call/tool_result 写入 AgentContext 的
 * 旁路通道）；无监听器场景（非对话链路）传 null 即可。</p>
 *
 * @author gj-llm
 */
public interface ToolCallListener {

    /** 工具调用开始 */
    void onCall(String toolName, String argsPreview);

    /** 工具调用结束（失败时 errorMessage 非空） */
    void onResult(String toolName, boolean ok, String resultPreview, long costMs, String errorMessage);
}
