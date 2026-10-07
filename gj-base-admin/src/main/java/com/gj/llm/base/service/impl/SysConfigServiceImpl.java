package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.SysConfigEntity;
import com.gj.llm.base.mapper.SysConfigMapper;
import com.gj.llm.base.model.SysConfigCreateRequest;
import com.gj.llm.base.model.SysConfigUpdateRequest;
import com.gj.llm.base.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参数配置服务实现。
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfigEntity> implements SysConfigService {

    @Override
    public IPage<SysConfigEntity> page(long page, long size, String keyword) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<SysConfigEntity>()
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(SysConfigEntity::getName, keyword).or().like(SysConfigEntity::getConfigKey, keyword))
                .orderByDesc(SysConfigEntity::getCreatedAt));
    }

    @Override
    public SysConfigEntity getByKey(String key) {
        return getOne(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getConfigKey, key)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional
    public SysConfigEntity create(SysConfigCreateRequest request) {
        long count = count(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getConfigKey, request.getConfigKey()));
        if (count > 0) {
            throw new RuntimeException("参数键名已存在: " + request.getConfigKey());
        }
        SysConfigEntity entity = SysConfigEntity.builder()
                .name(request.getName())
                .configKey(request.getConfigKey())
                .configValue(request.getConfigValue() == null ? "" : request.getConfigValue())
                .remark(request.getRemark())
                .build();
        save(entity);
        log.info("创建参数: {}={}", entity.getConfigKey(), entity.getConfigValue());
        return entity;
    }

    @Override
    @Transactional
    public SysConfigEntity update(Long id, SysConfigUpdateRequest request) {
        SysConfigEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("参数不存在");
        }
        entity.setName(request.getName());
        entity.setConfigValue(request.getConfigValue() == null ? "" : request.getConfigValue());
        entity.setRemark(request.getRemark());
        updateById(entity);
        log.info("更新参数: {}={}", entity.getConfigKey(), entity.getConfigValue());
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysConfigEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("参数不存在");
        }
        if (entity.getBuiltIn() != null && entity.getBuiltIn() == 1) {
            throw new RuntimeException("内置参数不允许删除: " + entity.getConfigKey());
        }
        removeById(id);
        log.info("删除参数: {}", entity.getConfigKey());
    }
}
