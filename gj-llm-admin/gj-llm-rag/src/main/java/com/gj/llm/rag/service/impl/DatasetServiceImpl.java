package com.gj.llm.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.base.entity.ResourceAclEntity;
import com.gj.llm.base.service.GrantService;
import com.gj.llm.base.service.ResourceAclService;
import com.gj.llm.common.util.SecurityUtils;
import com.gj.llm.common.util.StringUtils;
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
import com.gj.llm.rag.service.DatasetVisibleService;
import com.gj.llm.rag.service.QueryPlanner;
import com.gj.llm.rag.vector.DynamicVectorStoreManager;
import com.gj.llm.redis.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DatasetServiceImpl extends ServiceImpl<DatasetMapper, DatasetEntity> implements DatasetService {

    private final DynamicVectorStoreManager storeManager;
    private final EsSearchService esSearchService;
    private final DatasetFileMapper datasetFileMapper;
    private final DocumentSegmentMapper segmentMapper;
    private final FileStorageService fileStorageService;
    private final RedisService redisService;
    private final AuthProperties authProperties;
    private final GrantService grantService;
    private final ResourceAclService resourceAclService;

    public DatasetServiceImpl(DynamicVectorStoreManager storeManager,
                              EsSearchService esSearchService,
                              DatasetFileMapper datasetFileMapper,
                              DocumentSegmentMapper segmentMapper,
                              FileStorageService fileStorageService,
                              RedisService redisService,
                              AuthProperties authProperties,
                              GrantService grantService,
                              ResourceAclService resourceAclService) {
        this.storeManager = storeManager;
        this.esSearchService = esSearchService;
        this.datasetFileMapper = datasetFileMapper;
        this.segmentMapper = segmentMapper;
        this.fileStorageService = fileStorageService;
        this.redisService = redisService;
        this.authProperties = authProperties;
        this.grantService = grantService;
        this.resourceAclService = resourceAclService;
    }

    /** 库增删改后失效智能路由的库清单缓存与用户可见域缓存(60s TTL 只是兜底,主动失效保新) */
    private void invalidateRouteCache() {
        try {
            redisService.delete(QueryPlanner.DATASET_CACHE_KEY);
            redisService.deleteByPattern(DatasetVisibleService.VISIBLE_CACHE_PATTERN);
        } catch (Exception e) {
            log.warn("失效路由/可见域缓存失败(60s TTL 兜底): {}", e.getMessage());
        }
    }

    @Override
    public IPage<DatasetEntity> page(int page, int pageSize) {
        return baseMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<DatasetEntity>().orderByDesc(DatasetEntity::getCreatedAt));
    }

    @Override
    public IPage<DatasetEntity> pageForUser(Long userId, int page, int pageSize) {
        // 管理员不受限
        if (userId != null && grantService.isAdmin(userId)) {
            return page(page, pageSize);
        }
        LambdaQueryWrapper<DatasetEntity> wrapper = new LambdaQueryWrapper<DatasetEntity>()
                // PUBLIC（老数据 NULL 视同 PUBLIC）
                .and(v -> v.ne(DatasetEntity::getVisibility, AuthProperties.VISIBILITY_RESTRICTED)
                        .or().isNull(DatasetEntity::getVisibility))
                // 本人创建
                .or(v -> v.eq(DatasetEntity::getOwnerId, userId));
        if (userId != null) {
            Set<Long> grants = grantService.grantedResourceIds(userId, ResourceAclEntity.RESOURCE_TYPE_DATASET);
            if (!grants.isEmpty()) {
                wrapper.or(v -> v.in(DatasetEntity::getId, grants));
            }
        }
        return baseMapper.selectPage(
                new Page<>(page, pageSize),
                wrapper.orderByDesc(DatasetEntity::getCreatedAt));
    }

    @Override
    public List<DatasetEntity> listAll() {
        return list(new LambdaQueryWrapper<DatasetEntity>().orderByDesc(DatasetEntity::getCreatedAt));
    }

    @Override
    @Transactional
    public DatasetEntity create(DatasetCreateRequest request) {
        long count = count(new LambdaQueryWrapper<DatasetEntity>().eq(DatasetEntity::getName, request.getName()));
        if (count > 0) {
            throw new RuntimeException("知识库名称已存在: " + request.getName());
        }

        // 统一处理集合名称：去掉可能的前缀，保留纯 type；未填则用库名生成
        String typeName = request.getCollectionName();
        if (StringUtils.isBlank(typeName)) {
            typeName = request.getName().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        } else if (typeName.startsWith(VectorStoreConstants.COLLECTION_PREFIX)) {
            typeName = typeName.substring(VectorStoreConstants.COLLECTION_PREFIX.length());
        }
        final String finalTypeName = typeName;

        count = count(new LambdaQueryWrapper<DatasetEntity>().eq(DatasetEntity::getCollectionName, finalTypeName));
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
                .ownerId(SecurityUtils.getCurrentUserId())
                .visibility(authProperties.getDefaultVisibility())
                .build();
        save(entity);

        // 在 Milvus 中创建对应的集合（DynamicVectorStoreManager 会自动加 collection_ 前缀）
        storeManager.getVectorStore(finalTypeName);
        log.info("Milvus 集合创建/确认成功: {}{}", VectorStoreConstants.COLLECTION_PREFIX, finalTypeName);

        invalidateRouteCache();
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
        if (request.getRerankScoreThreshold() != null) {
            entity.setRerankScoreThreshold(request.getRerankScoreThreshold());
        }
        updateById(entity);
        invalidateRouteCache();
        log.info("更新知识库成功: id={}", id);
        return entity;
    }

    @Override
    public void adjustCounters(Long id, int docDelta, int segmentDelta) {
        if (docDelta == 0 && segmentDelta == 0) {
            return;
        }
        int rows = baseMapper.adjustCounters(id, docDelta, segmentDelta);
        if (rows == 0) {
            log.warn("知识库计数调整未生效（知识库不存在）: id={}", id);
        }
    }

    @Override
    public void updateVisibility(Long id, String visibility) {
        if (!AuthProperties.VISIBILITY_PUBLIC.equals(visibility)
                && !AuthProperties.VISIBILITY_RESTRICTED.equals(visibility)) {
            throw new RuntimeException("非法可见性取值: " + visibility + "（仅支持 PUBLIC/RESTRICTED）");
        }
        DatasetEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("知识库不存在: id=" + id);
        }
        entity.setVisibility(visibility);
        updateById(entity);
        invalidateRouteCache();
        log.info("切换知识库可见性成功: id={}, visibility={}", id, visibility);
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

        // 4. 删除知识库记录与授权关系
        removeById(id);
        resourceAclService.deleteByResource(ResourceAclEntity.RESOURCE_TYPE_DATASET, id);
        invalidateRouteCache();

        log.info("删除知识库成功: id={}, collectionName={}, 清理文件数={}", id, entity.getCollectionName(), files.size());
    }
}
