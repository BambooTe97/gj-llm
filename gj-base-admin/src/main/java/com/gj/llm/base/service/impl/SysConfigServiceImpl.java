package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.common.exception.WarnBusinessException;
import com.gj.llm.common.util.StringUtils;
import com.gj.llm.base.entity.SysConfigEntity;
import com.gj.llm.base.event.ConfigChangedEvent;
import com.gj.llm.base.mapper.SysConfigMapper;
import com.gj.llm.base.model.SysConfigCreateRequest;
import com.gj.llm.base.model.SysConfigUpdateRequest;
import com.gj.llm.base.service.SysConfigService;
import com.gj.llm.base.util.CacheKeyGuard;
import com.gj.llm.redis.constant.CacheConstants;
import com.gj.llm.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 参数配置服务实现。
 *
 * <p>{@link #getByKey} 为全员高频消费端点，启用 Redis 读缓存
 * （key={@code sys:config:key:{key}}，TTL 30 分钟兜底），增删改通过
 * {@link ConfigChangedEvent} 事务提交后按键名失效（键名创建后不可改，删除按旧键失效）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfigEntity> implements SysConfigService {

    private final RedisService redisService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public IPage<SysConfigEntity> page(long page, long size, String keyword) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<SysConfigEntity>()
                .and(StringUtils.isNotBlank(keyword),
                        w -> w.like(SysConfigEntity::getName, StringUtils.escapeLike(keyword.trim()))
                                .or().like(SysConfigEntity::getConfigKey, StringUtils.escapeLike(keyword.trim())))
                .orderByDesc(SysConfigEntity::getCreatedAt));
    }

    @Override
    public SysConfigEntity getByKey(String key) {
        // configKey 直接拼缓存 key，格式不合法的入参直接拒绝（防灌无主缓存键）
        CacheKeyGuard.check(key, "参数键名");
        String cacheKey = CacheConstants.SYS_CONFIG_KEY + key;
        SysConfigEntity cached = redisService.get(cacheKey, SysConfigEntity.class);
        if (cached != null) {
            return cached;
        }
        SysConfigEntity entity = getOne(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getConfigKey, key)
                .last("LIMIT 1"));
        if (entity != null) {
            redisService.set(cacheKey, entity, Duration.ofMinutes(CacheConstants.SYS_CACHE_TTL_MINUTES));
        }
        return entity;
    }

    @Override
    @Transactional
    public SysConfigEntity create(SysConfigCreateRequest request) {
        long count = count(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getConfigKey, request.getConfigKey()));
        if (count > 0) {
            throw new WarnBusinessException("config.keyExists", request.getConfigKey());
        }
        SysConfigEntity entity = SysConfigEntity.builder()
                .name(request.getName())
                .configKey(request.getConfigKey())
                .configValue(request.getConfigValue() == null ? "" : request.getConfigValue())
                .remark(request.getRemark())
                .build();
        save(entity);
        eventPublisher.publishEvent(new ConfigChangedEvent(entity.getConfigKey()));
        // 值可能含敏感信息，日志只记键名
        log.info("创建参数: {}", entity.getConfigKey());
        return entity;
    }

    @Override
    @Transactional
    public SysConfigEntity update(Long id, SysConfigUpdateRequest request) {
        SysConfigEntity entity = getById(id);
        if (entity == null) {
            throw new WarnBusinessException("config.notFound");
        }
        entity.setName(request.getName());
        entity.setConfigValue(request.getConfigValue() == null ? "" : request.getConfigValue());
        entity.setRemark(request.getRemark());
        updateById(entity);
        eventPublisher.publishEvent(new ConfigChangedEvent(entity.getConfigKey()));
        // 值可能含敏感信息，日志只记键名
        log.info("更新参数: {}", entity.getConfigKey());
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysConfigEntity entity = getById(id);
        if (entity == null) {
            throw new WarnBusinessException("config.notFound");
        }
        if (entity.getBuiltIn() != null && entity.getBuiltIn() == 1) {
            throw new WarnBusinessException("config.builtinDeleteDenied", entity.getConfigKey());
        }
        removeById(id);
        eventPublisher.publishEvent(new ConfigChangedEvent(entity.getConfigKey()));
        log.info("删除参数: {}", entity.getConfigKey());
    }
}
