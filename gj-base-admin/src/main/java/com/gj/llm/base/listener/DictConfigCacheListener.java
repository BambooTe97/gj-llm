package com.gj.llm.base.listener;

import com.gj.llm.base.event.ConfigChangedEvent;
import com.gj.llm.base.event.DictChangedEvent;
import com.gj.llm.redis.constant.CacheConstants;
import com.gj.llm.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 字典/参数读缓存失效监听器 -- 事务提交后按事件参数删除对应缓存键。
 *
 * <p>事件参数为 {@code null} 时全量失效（deleteByPattern），对应字典类型改名/删除这类
 * 按旧 key 无法定位的场景；非 null 时精确删单键。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictConfigCacheListener {

    private final RedisService redisService;

    /**
     * 字典变更 -> 事务提交后失效该类型字典缓存（null=全量）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDictChanged(DictChangedEvent event) {
        if (event.dictType() == null) {
            long n = redisService.deleteByPattern(CacheConstants.SYS_DICT_DATA_KEY + "*");
            log.info("全量失效字典缓存: {} 条", n);
        } else {
            redisService.delete(CacheConstants.SYS_DICT_DATA_KEY + event.dictType());
        }
    }

    /**
     * 参数变更 -> 事务提交后失效该键名参数缓存（null=全量）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfigChanged(ConfigChangedEvent event) {
        if (event.configKey() == null) {
            long n = redisService.deleteByPattern(CacheConstants.SYS_CONFIG_KEY + "*");
            log.info("全量失效参数缓存: {} 条", n);
        } else {
            redisService.delete(CacheConstants.SYS_CONFIG_KEY + event.configKey());
        }
    }
}
