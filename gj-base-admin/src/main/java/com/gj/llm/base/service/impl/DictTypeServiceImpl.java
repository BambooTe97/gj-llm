package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.DictDataEntity;
import com.gj.llm.base.entity.DictTypeEntity;
import com.gj.llm.base.event.DictChangedEvent;
import com.gj.llm.base.mapper.DictTypeMapper;
import com.gj.llm.base.model.DictTypeCreateRequest;
import com.gj.llm.base.model.DictTypeUpdateRequest;
import com.gj.llm.base.service.DictDataService;
import com.gj.llm.base.service.DictTypeService;
import com.gj.llm.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 字典类型服务实现。
 *
 * <p>type 编码改名校验唯一后级联更新字典数据（{@code sys_dict_data.dict_type} 冗余存储）；
 * 类型下存在数据时拒绝删除。改名/删除会发布 {@link DictChangedEvent}（dictType=null 全量），
 * 因数据行的 type 编码已随级联变化，按旧类型失效无法覆盖。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictTypeServiceImpl extends ServiceImpl<DictTypeMapper, DictTypeEntity> implements DictTypeService {

    private final DictDataService dictDataService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public IPage<DictTypeEntity> page(long page, long size, String keyword, Integer status) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<DictTypeEntity>()
                .and(StringUtils.isNotBlank(keyword),
                        w -> w.like(DictTypeEntity::getName, keyword).or().like(DictTypeEntity::getType, keyword))
                .eq(status != null, DictTypeEntity::getStatus, status)
                .orderByDesc(DictTypeEntity::getCreatedAt));
    }

    @Override
    @Transactional
    public DictTypeEntity create(DictTypeCreateRequest request) {
        long count = count(new LambdaQueryWrapper<DictTypeEntity>().eq(DictTypeEntity::getType, request.getType()));
        if (count > 0) {
            throw new RuntimeException("字典类型已存在: " + request.getType());
        }
        DictTypeEntity entity = DictTypeEntity.builder()
                .name(request.getName())
                .type(request.getType())
                .remark(request.getRemark())
                .build();
        save(entity);
        log.info("创建字典类型: {}", entity.getType());
        return entity;
    }

    @Override
    @Transactional
    public DictTypeEntity update(Long id, DictTypeUpdateRequest request) {
        DictTypeEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("字典类型不存在");
        }
        // type 改名：唯一性校验 + 级联更新字典数据
        if (!Objects.equals(entity.getType(), request.getType())) {
            long count = count(new LambdaQueryWrapper<DictTypeEntity>().eq(DictTypeEntity::getType, request.getType()));
            if (count > 0) {
                throw new RuntimeException("字典类型已存在: " + request.getType());
            }
            String oldType = entity.getType();
            dictDataService.update(new LambdaUpdateWrapper<DictDataEntity>()
                    .eq(DictDataEntity::getDictType, oldType)
                    .set(DictDataEntity::getDictType, request.getType()));
            eventPublisher.publishEvent(new DictChangedEvent(null));
            log.info("字典类型改名: {} -> {}（级联更新数据）", oldType, request.getType());
        }
        entity.setName(request.getName());
        entity.setType(request.getType());
        entity.setRemark(request.getRemark());
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DictTypeEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("字典类型不存在");
        }
        long dataCount = dictDataService.count(new LambdaQueryWrapper<DictDataEntity>()
                .eq(DictDataEntity::getDictType, entity.getType()));
        if (dataCount > 0) {
            throw new RuntimeException("该类型下存在 " + dataCount + " 条字典数据，请先删除数据");
        }
        removeById(id);
        eventPublisher.publishEvent(new DictChangedEvent(null));
        log.info("删除字典类型: {}", entity.getType());
    }
}
