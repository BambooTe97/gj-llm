package com.gj.llm.base.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 基座模块调度开关 -- 供 {@code LogCleanJob} 日志保留期清理使用。
 *
 * <p>{@code @EnableScheduling} 幂等，与 mcp 模块的 {@code McpScheduleConfig} 并存无冲突；
 * 基座独立打包/测试时也不依赖 mcp 模块开启。</p>
 *
 * @author gj-llm
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
