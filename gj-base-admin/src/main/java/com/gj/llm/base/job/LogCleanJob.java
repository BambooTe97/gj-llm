package com.gj.llm.base.job;

import com.gj.llm.base.config.LogProperties;
import com.gj.llm.base.service.LoginLogService;
import com.gj.llm.base.service.OperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 审计日志保留期清理任务 -- 每日 03:30 分批删除超过保留期的操作日志/登录日志。
 *
 * <p>保留天数 {@code gj.llm.log.retention-days}（默认 90，0=关闭自动清理）。
 * 删除由各 service 的 {@code clearBefore} 分批执行（每批 1000），避免长事务锁表；
 * 失败只记 ERROR，等下一次调度重试。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogCleanJob {

    private final LogProperties logProperties;
    private final OperLogService operLogService;
    private final LoginLogService loginLogService;

    /** 每日 03:30 执行 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void clean() {
        int retentionDays = logProperties.getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime threshold = LocalDate.now().minusDays(retentionDays).atStartOfDay();
        log.info("[日志清理] 开始清理 {} 之前的审计日志", threshold);
        try {
            int oper = operLogService.clearBefore(threshold);
            int login = loginLogService.clearBefore(threshold);
            log.info("[日志清理] 完成: 操作日志 {} 条, 登录日志 {} 条", oper, login);
        } catch (Exception e) {
            log.error("[日志清理] 清理失败（等待下次调度重试）", e);
        }
    }
}
