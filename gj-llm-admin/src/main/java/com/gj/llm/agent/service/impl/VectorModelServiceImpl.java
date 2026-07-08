package com.gj.llm.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.agent.entity.VectorModelEntity;
import com.gj.llm.agent.mapper.VectorModelMapper;
import com.gj.llm.agent.model.VectorModelCreateRequest;
import com.gj.llm.agent.model.VectorModelUpdateRequest;
import com.gj.llm.agent.service.VectorModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class VectorModelServiceImpl extends ServiceImpl<VectorModelMapper, VectorModelEntity>
        implements VectorModelService {

    @Override
    public IPage<VectorModelEntity> page(int page, int pageSize) {
        return baseMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<VectorModelEntity>().orderByDesc(VectorModelEntity::getCreatedAt));
    }

    @Override
    public List<VectorModelEntity> listAll() {
        return list(new LambdaQueryWrapper<VectorModelEntity>().orderByDesc(VectorModelEntity::getCreatedAt));
    }

    @Override
    public VectorModelEntity getByTypeCode(String typeCode) {
        return getOne(new LambdaQueryWrapper<VectorModelEntity>().eq(VectorModelEntity::getTypeCode, typeCode));
    }

    @Override
    @Transactional
    public VectorModelEntity create(VectorModelCreateRequest request) {
        long count = count(new LambdaQueryWrapper<VectorModelEntity>()
                .eq(VectorModelEntity::getTypeCode, request.getTypeCode()));
        if (count > 0) {
            throw new RuntimeException("类型编码已存在: " + request.getTypeCode());
        }
        count = count(new LambdaQueryWrapper<VectorModelEntity>()
                .eq(VectorModelEntity::getCollectionName, request.getCollectionName()));
        if (count > 0) {
            throw new RuntimeException("集合名称已存在: " + request.getCollectionName());
        }

        VectorModelEntity entity = VectorModelEntity.builder()
                .typeCode(request.getTypeCode())
                .typeName(request.getTypeName())
                .collectionName(request.getCollectionName())
                .description(request.getDescription())
                .build();
        save(entity);
        log.info("创建向量模型库成功: typeCode={}, collectionName={}", entity.getTypeCode(), entity.getCollectionName());
        return entity;
    }

    @Override
    @Transactional
    public VectorModelEntity update(Long id, VectorModelUpdateRequest request) {
        VectorModelEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("向量模型库不存在: id=" + id);
        }
        if (request.getTypeName() != null) {
            entity.setTypeName(request.getTypeName());
        }
        if (request.getCollectionName() != null
                && !entity.getCollectionName().equals(request.getCollectionName())) {
            long count = count(new LambdaQueryWrapper<VectorModelEntity>()
                    .eq(VectorModelEntity::getCollectionName, request.getCollectionName()));
            if (count > 0) {
                throw new RuntimeException("集合名称已存在: " + request.getCollectionName());
            }
            entity.setCollectionName(request.getCollectionName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        updateById(entity);
        log.info("更新向量模型库成功: id={}", id);
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new RuntimeException("向量模型库不存在: id=" + id);
        }
        removeById(id);
    }
}
