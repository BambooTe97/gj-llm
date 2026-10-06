package com.gj.llm.mcp.model;

/**
 * 外部 server 连接测试结果。
 *
 * @param ok        是否连通并完成 MCP 初始化
 * @param message   结果说明（失败原因）
 * @param toolCount 发现的工具数量
 * @author gj-llm
 */
public record ConnectionTestResult(boolean ok, String message, int toolCount) {
}
