package com.gj.llm.mcp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MCP 调度开关 -- 全仓首个 @EnableScheduling，供 {@code McpHealthChecker} 定时健康检查使用。
 *
 * @author gj-llm
 */
@Configuration
@EnableScheduling
public class McpScheduleConfig {
}
