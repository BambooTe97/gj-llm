package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.DictDataEntity;
import com.gj.llm.base.entity.DictTypeEntity;
import com.gj.llm.base.event.DictChangedEvent;
import com.gj.llm.base.mapper.DictDataMapper;
import com.gj.llm.base.mapper.DictTypeMapper;
import com.gj.llm.base.model.DictDataCreateRequest;
import com.gj.llm.base.model.DictDataUpdateRequest;
import com.gj.llm.base.service.DictDataService;
import com.gj.llm.base.util.CacheKeyGuard;
import com.gj.llm.common.exception.WarnBusinessException;
import com.gj.llm.common.util.StringUtils;
import com.gj.llm.redis.constant.CacheConstants;
import com.gj.llm.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 字典数据服务实现。
 *
 * <p>{@link #listByType} 为全员高频消费端点（登录页下拉等），启用 Redis 读缓存
 * （key={@code sys:dict:data:{type}}，TTL 30 分钟兜底），增删改通过
 * {@link DictChangedEvent} 事务提交后按类型失效。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictDataServiceImpl extends ServiceImpl<DictDataMapper, DictDataEntity> implements DictDataService {

    private final RedisService redisService;
    private final ApplicationEventPublisher eventPublisher;
    private final DictTypeMapper dictTypeMapper;

    @Override
    public IPage<DictDataEntity> page(long page, long size, String dictType, String keyword) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<DictDataEntity>()
                .eq(DictDataEntity::getDictType, dictType)
                .and(StringUtils.isNotBlank(keyword),
                        w -> w.like(DictDataEntity::getLabel, keyword).or().like(DictDataEntity::getDictValue, keyword))
                .orderByAsc(DictDataEntity::getSort));
    }

    @Override
    public List<DictDataEntity> listByType(String dictType) {
        // dictType 直接拼缓存 key，格式不合法的入参直接拒绝（防灌无主缓存键）
        CacheKeyGuard.check(dictType, "字典类型");
        String key = CacheConstants.SYS_DICT_DATA_KEY + dictType;
        // 数组类型承载 List 元素类型信息（Jackson 泛型擦除规避；本模块未直依赖 jackson-core，不用 TypeReference）
        DictDataEntity[] cached = redisService.get(key, DictDataEntity[].class);
        if (cached != null) {
            return Arrays.asList(cached);
        }
        List<DictDataEntity> data = list(new LambdaQueryWrapper<DictDataEntity>()
                .eq(DictDataEntity::getDictType, dictType)
                .eq(DictDataEntity::getStatus, 1)
                .orderByAsc(DictDataEntity::getSort));
        redisService.set(key, data, Duration.ofMinutes(CacheConstants.SYS_CACHE_TTL_MINUTES));
        return data;
    }

    @Override
    @Transactional
    public DictDataEntity create(DictDataCreateRequest request) {
        // dictType 必须是已存在的字典类型，防止造出管理页看不到类型的孤儿数据
        CacheKeyGuard.check(request.getDictType(), "字典类型");
        if (dictTypeMapper.selectCount(new LambdaQueryWrapper<DictTypeEntity>()
                .eq(DictTypeEntity::getType, request.getDictType())) == 0) {
            throw new WarnBusinessException("dictType.notFound", request.getDictType());
        }
        checkValueUnique(request.getDictType(), request.getDictValue(), null);
        DictDataEntity entity = DictDataEntity.builder()
                .dictType(request.getDictType())
                .label(request.getLabel())
                .dictValue(request.getDictValue())
                .sort(request.getSort() == null ? 0 : request.getSort())
                .remark(request.getRemark())
                .build();
        save(entity);
        eventPublisher.publishEvent(new DictChangedEvent(entity.getDictType()));
        log.info("创建字典数据: {}={}", entity.getDictType(), entity.getDictValue());
        return entity;
    }

    @Override
    @Transactional
    public DictDataEntity update(Long id, DictDataUpdateRequest request) {
        DictDataEntity entity = getById(id);
        if (entity == null) {
            throw new WarnBusinessException("dictData.notFound");
        }
        checkValueUnique(entity.getDictType(), request.getDictValue(), id);
        entity.setLabel(request.getLabel());
        entity.setDictValue(request.getDictValue());
        entity.setRemark(request.getRemark());
        if (request.getSort() != null) {
            entity.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        updateById(entity);
        eventPublisher.publishEvent(new DictChangedEvent(entity.getDictType()));
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DictDataEntity entity = getById(id);
        if (entity == null) {
            throw new WarnBusinessException("dictData.notFound");
        }
        removeById(id);
        eventPublisher.publishEvent(new DictChangedEvent(entity.getDictType()));
        log.info("删除字典数据: {}={}", entity.getDictType(), entity.getDictValue());
    }

    /** 同类型下 dictValue 唯一校验（排除自身） */
    private void checkValueUnique(String dictType, String dictValue, Long excludeId) {
        long count = count(new LambdaQueryWrapper<DictDataEntity>()
                .eq(DictDataEntity::getDictType, dictType)
                .eq(DictDataEntity::getDictValue, dictValue)
                .ne(excludeId != null, DictDataEntity::getId, excludeId));
        if (count > 0) {
            throw new WarnBusinessException("dictData.valueExists", dictValue);
        }
    }
}
