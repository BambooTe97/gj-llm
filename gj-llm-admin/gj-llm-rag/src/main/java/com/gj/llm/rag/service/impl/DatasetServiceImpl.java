package com.gj.llm.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.mapper.DatasetMapper;
import com.gj.llm.rag.model.DatasetCreateRequest;
import com.gj.llm.rag.model.DatasetUpdateRequest;
import com.gj.llm.rag.constant.VectorStoreConstants;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.rag.vector.DynamicVectorStoreManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class DatasetServiceImpl extends ServiceImpl<DatasetMapper, DatasetEntity> implements DatasetService {

    private final DynamicVectorStoreManager storeManager;

    public DatasetServiceImpl(DynamicVectorStoreManager storeManager) {
        this.storeManager = storeManager;
    }

    @Override
    public IPage<DatasetEntity> page(int page, int pageSize) {
        return baseMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<DatasetEntity>().orderByDesc(DatasetEntity::getCreatedAt));
    }

    @Override
    public List<DatasetEntity> listAll() {
        return list(new LambdaQueryWrapper<DatasetEntity>().orderByDesc(DatasetEntity::getCreatedAt));
    }

    @Override
    @Transactional
    public DatasetEntity create(DatasetCreateRequest request) {
        long count = count(new LambdaQueryWrapper<DatasetEntity>()
                .eq(DatasetEntity::getName, request.getName()));
        if (count > 0) {
            throw new RuntimeException("知识库名称已存在: " + request.getName());
        }

        // 统一处理集合名称：去掉可能的前缀，保留纯 type；未填则用库名生成
        String typeName = request.getCollectionName();
        if (typeName == null || typeName.isBlank()) {
            typeName = request.getName().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        } else if (typeName.startsWith(VectorStoreConstants.COLLECTION_PREFIX)) {
            typeName = typeName.substring(VectorStoreConstants.COLLECTION_PREFIX.length());
        }
        final String finalTypeName = typeName;

        count = count(new LambdaQueryWrapper<DatasetEntity>()
                .eq(DatasetEntity::getCollectionName, finalTypeName));
        if (count > 0) {
            throw new RuntimeException("集合名称已存在: " + finalTypeName);
        }

        DatasetEntity entity = DatasetEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .embeddingModel(request.getEmbeddingModel())
                .vectorStoreType(request.getVectorStoreType())
                .collectionName(finalTypeName)
                .chunkSize(request.getChunkSize() != null ? request.getChunkSize() : 800)
                .chunkOverlap(request.getChunkOverlap() != null ? request.getChunkOverlap() : 100)
                .build();
        save(entity);

        // 在 Milvus 中创建对应的集合（DynamicVectorStoreManager 会自动加 collection_ 前缀）
        storeManager.getVectorStore(finalTypeName);
        log.info("Milvus 集合创建/确认成功: {}{}", VectorStoreConstants.COLLECTION_PREFIX, finalTypeName);

        log.info("创建知识库成功: name={}, collectionName={}", entity.getName(), entity.getCollectionName());
        return entity;
    }

    @Override
    @Transactional
    public DatasetEntity update(Long id, DatasetUpdateRequest request) {
        DatasetEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("知识库不存在: id=" + id);
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getEmbeddingModel() != null) {
            entity.setEmbeddingModel(request.getEmbeddingModel());
        }
        if (request.getChunkSize() != null) {
            entity.setChunkSize(request.getChunkSize());
        }
        if (request.getChunkOverlap() != null) {
            entity.setChunkOverlap(request.getChunkOverlap());
        }
        updateById(entity);
        log.info("更新知识库成功: id={}", id);
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DatasetEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("知识库不存在: id=" + id);
        }
        removeById(id);
        log.info("删除知识库成功: id={}, collectionName={}", id, entity.getCollectionName());
    }
}
