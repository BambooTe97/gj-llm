package com.gj.llm.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 审计日志保留配置 —— 操作日志/登录日志的定时清理策略。
 *
 * <p>配置前缀 {@code gj.llm.log}。</p>
 *
 * @author gj-llm
 */
@Data
@Component
@ConfigurationProperties(prefix = "gj.llm.log")
public class LogProperties {

    /**
     * 审计日志保留天数（sys_oper_log / sys_logininfor）。
     * <p>0 或负数 = 关闭自动清理（只保留页面手动清空能力）。</p>
     */
    private int retentionDays = 90;
}
