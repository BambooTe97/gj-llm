package com.gj.llm.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.es.service.EsSearchService;
import com.gj.llm.file.service.FileStorageService;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.entity.DatasetFileEntity;
import com.gj.llm.rag.entity.DocumentSegmentEntity;
import com.gj.llm.rag.mapper.DatasetFileMapper;
import com.gj.llm.rag.mapper.DatasetMapper;
import com.gj.llm.rag.mapper.DocumentSegmentMapper;
import com.gj.llm.rag.model.DatasetCreateRequest;
import com.gj.llm.rag.model.DatasetUpdateRequest;
import com.gj.llm.rag.constant.VectorStoreConstants;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.rag.vector.DynamicVectorStoreManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DatasetServiceImpl extends ServiceImpl<DatasetMapper, DatasetEntity> implements DatasetService {

    private final DynamicVectorStoreManager storeManager;
    private final EsSearchService esSearchService;
    private final DatasetFileMapper datasetFileMapper;
    private final DocumentSegmentMapper segmentMapper;
    private final FileStorageService fileStorageService;

    public DatasetServiceImpl(DynamicVectorStoreManager storeManager,
                              EsSearchService esSearchService,
                              DatasetFileMapper datasetFileMapper,
                              DocumentSegmentMapper segmentMapper,
                              FileStorageService fileStorageService) {
        this.storeManager = storeManager;
        this.esSearchService = esSearchService;
        this.datasetFileMapper = datasetFileMapper;
        this.segmentMapper = segmentMapper;
        this.fileStorageService = fileStorageService;
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
                .chunkSize(request.getChunkSize() != null ? request.getChunkSize() : 600)
                .chunkOverlap(request.getChunkOverlap() != null ? request.getChunkOverlap() : 150)
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

        // 1. 逐个清理关联文件（ES文档 + 物理文件 + segments + dataset_file）
        List<DatasetFileEntity> files = datasetFileMapper.selectList(
                new LambdaQueryWrapper<DatasetFileEntity>()
                        .eq(DatasetFileEntity::getDatasetId, id));
        for (DatasetFileEntity df : files) {
            // 清理 ES 文档
            List<DocumentSegmentEntity> segments = segmentMapper.selectList(
                    new LambdaQueryWrapper<DocumentSegmentEntity>()
                            .eq(DocumentSegmentEntity::getDatasetFileId, df.getId()));
            if (!segments.isEmpty()) {
                try {
                    esSearchService.deleteDocuments(entity.getCollectionName(),
                            segments.stream().map(DocumentSegmentEntity::getSegmentId).collect(Collectors.toList()));
                } catch (Exception e) {
                    log.warn("删除ES文档失败: dfId={}", df.getId());
                }
                segmentMapper.delete(new LambdaQueryWrapper<DocumentSegmentEntity>()
                        .eq(DocumentSegmentEntity::getDatasetFileId, df.getId()));
            }
            // 清理物理文件
            try {
                fileStorageService.delete(df.getFileId());
            } catch (Exception e) {
                log.warn("删除物理文件失败: fileId={}", df.getFileId());
            }
            // 删除关联记录
            datasetFileMapper.deleteById(df.getId());
        }
        log.info("已清理 {} 个关联文件的向量数据和物理文件", files.size());

        // 2. 删除 Milvus 集合
        try {
            storeManager.dropCollection(entity.getCollectionName());
        } catch (Exception e) {
            log.warn("删除Milvus集合失败（可能不存在）: collectionName={}", entity.getCollectionName());
        }

        // 3. 删除 ES 索引
        try {
            esSearchService.deleteIndex(entity.getCollectionName());
        } catch (Exception e) {
            log.warn("删除ES索引失败（可能不存在）: collectionName={}", entity.getCollectionName());
        }

        // 4. 删除知识库记录
        removeById(id);

        log.info("删除知识库成功: id={}, collectionName={}, 清理文件数={}", id, entity.getCollectionName(), files.size());
    }
}
