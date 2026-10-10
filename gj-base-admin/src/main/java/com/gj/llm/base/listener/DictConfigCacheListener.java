package com.gj.llm.base.listener;

import com.gj.llm.base.event.ConfigChangedEvent;
import com.gj.llm.base.event.DictChangedEvent;
import com.gj.llm.redis.constant.CacheConstants;
import com.gj.llm.redis.service.RedisService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 字典/参数读缓存失效监听器 -- 事务提交后按事件参数删除对应缓存键。
 *
 * <p>事件参数为 {@code null} 时全量失效（deleteByPattern），对应字典类型改名/删除这类
 * 按旧 key 无法定位的场景；非 null 时精确删单键。</p>
 *
 * <h3>延迟双删</h3>
 * <p>"提交后删键"与读回填存在竞态：删键后、并发读仍可能拿旧值回填缓存，
 * 脏值可活满整个兜底 TTL。删键后延迟 1s 再删一次，回填窗口内的旧值也会被清掉。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictConfigCacheListener {

    /** 二次删除延迟（覆盖"删键后并发读旧值回填"的窗口） */
    private static final long SECOND_DELETE_DELAY_SECONDS = 1;

    private final RedisService redisService;

    /** 延迟双删调度器（单线程足矣，任务极轻） */
    private final ScheduledExecutorService delayedCleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cache-double-delete");
        t.setDaemon(true);
        return t;
    });

    /**
     * 字典变更 -> 事务提交后失效该类型字典缓存（null=全量），并延迟二次删除。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDictChanged(DictChangedEvent event) {
        if (event.dictType() == null) {
            long n = redisService.deleteByPattern(CacheConstants.SYS_DICT_DATA_KEY + "*");
            log.info("全量失效字典缓存: {} 条", n);
            scheduleSecondDelete(() -> redisService.deleteByPattern(CacheConstants.SYS_DICT_DATA_KEY + "*"));
        } else {
            redisService.delete(CacheConstants.SYS_DICT_DATA_KEY + event.dictType());
            scheduleSecondDelete(() -> redisService.delete(CacheConstants.SYS_DICT_DATA_KEY + event.dictType()));
        }
    }

    /**
     * 参数变更 -> 事务提交后失效该键名参数缓存（null=全量），并延迟二次删除。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfigChanged(ConfigChangedEvent event) {
        if (event.configKey() == null) {
            long n = redisService.deleteByPattern(CacheConstants.SYS_CONFIG_KEY + "*");
            log.info("全量失效参数缓存: {} 条", n);
            scheduleSecondDelete(() -> redisService.deleteByPattern(CacheConstants.SYS_CONFIG_KEY + "*"));
        } else {
            redisService.delete(CacheConstants.SYS_CONFIG_KEY + event.configKey());
            scheduleSecondDelete(() -> redisService.delete(CacheConstants.SYS_CONFIG_KEY + event.configKey()));
        }
    }

    /** 延迟二次删除（失败只记日志，不影响主链路） */
    private void scheduleSecondDelete(Runnable deleteTask) {
        delayedCleaner.schedule(() -> {
            try {
                deleteTask.run();
            } catch (Exception e) {
                log.warn("[缓存双删] 二次删除失败（等待 TTL 兜底）: {}", e.getMessage());
            }
        }, SECOND_DELETE_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        delayedCleaner.shutdown();
    }
}
