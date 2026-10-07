package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.DictDataEntity;
import com.gj.llm.base.mapper.DictDataMapper;
import com.gj.llm.base.model.DictDataCreateRequest;
import com.gj.llm.base.model.DictDataUpdateRequest;
import com.gj.llm.base.service.DictDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字典数据服务实现。
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class DictDataServiceImpl extends ServiceImpl<DictDataMapper, DictDataEntity> implements DictDataService {

    @Override
    public IPage<DictDataEntity> page(long page, long size, String dictType, String keyword) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<DictDataEntity>()
                .eq(DictDataEntity::getDictType, dictType)
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(DictDataEntity::getLabel, keyword).or().like(DictDataEntity::getDictValue, keyword))
                .orderByAsc(DictDataEntity::getSort));
    }

    @Override
    public List<DictDataEntity> listByType(String dictType) {
        return list(new LambdaQueryWrapper<DictDataEntity>()
                .eq(DictDataEntity::getDictType, dictType)
                .eq(DictDataEntity::getStatus, 1)
                .orderByAsc(DictDataEntity::getSort));
    }

    @Override
    @Transactional
    public DictDataEntity create(DictDataCreateRequest request) {
        checkValueUnique(request.getDictType(), request.getDictValue(), null);
        DictDataEntity entity = DictDataEntity.builder()
                .dictType(request.getDictType())
                .label(request.getLabel())
                .dictValue(request.getDictValue())
                .sort(request.getSort() == null ? 0 : request.getSort())
                .remark(request.getRemark())
                .build();
        save(entity);
        log.info("创建字典数据: {}={}", entity.getDictType(), entity.getDictValue());
        return entity;
    }

    @Override
    @Transactional
    public DictDataEntity update(Long id, DictDataUpdateRequest request) {
        DictDataEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("字典数据不存在");
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
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DictDataEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("字典数据不存在");
        }
        removeById(id);
        log.info("删除字典数据: {}={}", entity.getDictType(), entity.getDictValue());
    }

    /** 同类型下 dictValue 唯一校验（排除自身） */
    private void checkValueUnique(String dictType, String dictValue, Long excludeId) {
        long count = count(new LambdaQueryWrapper<DictDataEntity>()
                .eq(DictDataEntity::getDictType, dictType)
                .eq(DictDataEntity::getDictValue, dictValue)
                .ne(excludeId != null, DictDataEntity::getId, excludeId));
        if (count > 0) {
            throw new RuntimeException("该类型下键值已存在: " + dictValue);
        }
    }
}
