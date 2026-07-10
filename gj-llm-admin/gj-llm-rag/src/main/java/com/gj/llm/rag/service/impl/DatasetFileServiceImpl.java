package com.gj.llm.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.entity.DatasetFileEntity;
import com.gj.llm.rag.entity.DocumentSegmentEntity;
import com.gj.llm.rag.event.DatasetFileUploadedEvent;
import com.gj.llm.rag.mapper.DatasetFileMapper;
import com.gj.llm.rag.mapper.DocumentSegmentMapper;
import com.gj.llm.rag.model.DatasetFileVO;
import com.gj.llm.rag.service.DatasetFileService;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.rag.vector.DynamicVectorStoreManager;
import com.gj.llm.rag.vector.reader.FileContentReader;
import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.file.model.FileInfo;
import com.gj.llm.file.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DatasetFileServiceImpl extends ServiceImpl<DatasetFileMapper, DatasetFileEntity> implements DatasetFileService {

    private final ApplicationEventPublisher eventPublisher;
    private final DatasetService datasetService;
    private final FileStorageService fileStorageService;
    private final DynamicVectorStoreManager storeManager;
    private final DocumentSegmentMapper segmentMapper;
    private final List<FileContentReader> readers;

    public DatasetFileServiceImpl(ApplicationEventPublisher eventPublisher,
                                  DatasetService datasetService,
                                  FileStorageService fileStorageService,
                                  DynamicVectorStoreManager storeManager,
                                  DocumentSegmentMapper segmentMapper,
                                  List<FileContentReader> readers) {
        this.eventPublisher = eventPublisher;
        this.datasetService = datasetService;
        this.fileStorageService = fileStorageService;
        this.storeManager = storeManager;
        this.segmentMapper = segmentMapper;
        this.readers = readers;
    }

    @Override
    public IPage<DatasetFileVO> pageByDataset(Long datasetId, int page, int pageSize) {
        IPage<DatasetFileEntity> entityPage = baseMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<DatasetFileEntity>()
                        .eq(DatasetFileEntity::getDatasetId, datasetId)
                        .orderByDesc(DatasetFileEntity::getCreatedAt));

        List<DatasetFileVO> voList = entityPage.getRecords().stream().map(df -> {
            FileInfo fileInfo = null;
            try {
                fileInfo = fileStorageService.getById(df.getFileId());
            } catch (Exception e) {
                log.warn("获取文件信息失败: fileId={}", df.getFileId());
            }
            return DatasetFileVO.builder()
                    .id(df.getId())
                    .datasetId(df.getDatasetId())
                    .fileId(df.getFileId())
                    .fileName(fileInfo != null ? fileInfo.getOriginalName() : "(已删除)")
                    .fileType(fileInfo != null ? fileInfo.getExtension() : "")
                    .fileSize(fileInfo != null ? fileInfo.getSize() : 0L)
                    .fileUrl(fileInfo != null ? fileInfo.getUrl() : "")
                    .status(df.getStatus())
                    .errorMessage(df.getErrorMessage())
                    .segmentCount(df.getSegmentCount())
                    .createdAt(df.getCreatedAt())
                    .updatedAt(df.getUpdatedAt())
                    .build();
        }).toList();

        IPage<DatasetFileVO> voPage = new Page<>(page, pageSize);
        voPage.setRecords(voList);
        voPage.setTotal(entityPage.getTotal());
        return voPage;
    }

    @Override
    @Transactional
    public DatasetFileEntity upload(Long datasetId, MultipartFile file) {
        DatasetEntity dataset = datasetService.getById(datasetId);
        if (dataset == null) {
            throw new RuntimeException("知识库不存在: id=" + datasetId);
        }

        // 上传到物理文件存储（file_record 表）
        FileInfo fileInfo = fileStorageService.upload(file);

        // 创建知识库-文件关联记录
        DatasetFileEntity df = DatasetFileEntity.builder()
                .datasetId(datasetId)
                .fileId(fileInfo.getId())
                .status("PENDING")
                .build();
        save(df);

        // 更新知识库统计
        dataset.setDocCount(dataset.getDocCount() + 1);
        datasetService.updateById(dataset);

        // 发布事件，由 @TransactionalEventListener(afterCommit) 触发异步向量化
        eventPublisher.publishEvent(new DatasetFileUploadedEvent(df.getId()));

        log.info("知识库文件关联创建成功: datasetId={}, dfId={}, fileId={}", datasetId, df.getId(), fileInfo.getId());
        return df;
    }

    @Override
    @Transactional
    public void delete(Long datasetFileId) {
        DatasetFileEntity df = getById(datasetFileId);
        if (df == null) {
            throw new RuntimeException("关联记录不存在: id=" + datasetFileId);
        }

        DatasetEntity dataset = datasetService.getById(df.getDatasetId());

        // 删除向量数据
        if (dataset != null && df.getSegmentCount() > 0) {
            try {
                List<DocumentSegmentEntity> segments = segmentMapper.selectList(
                        new LambdaQueryWrapper<DocumentSegmentEntity>()
                                .eq(DocumentSegmentEntity::getDatasetFileId, datasetFileId));
                if (!segments.isEmpty()) {
                    VectorStore vectorStore = storeManager.getVectorStore(dataset.getCollectionName());
                    List<String> segmentIds = segments.stream()
                            .map(DocumentSegmentEntity::getSegmentId)
                            .collect(Collectors.toList());
                    vectorStore.delete(segmentIds);
                    segmentMapper.delete(new LambdaQueryWrapper<DocumentSegmentEntity>()
                            .eq(DocumentSegmentEntity::getDatasetFileId, datasetFileId));
                }
            } catch (Exception e) {
                log.error("删除向量数据失败: dfId={}", datasetFileId, e);
            }
        }

        // 删除物理文件
        try {
            fileStorageService.delete(df.getFileId());
        } catch (Exception e) {
            log.error("删除物理文件失败: fileId={}", df.getFileId(), e);
        }

        // 删除关联记录
        removeById(datasetFileId);

        // 更新知识库统计
        if (dataset != null && dataset.getDocCount() > 0) {
            dataset.setDocCount(dataset.getDocCount() - 1);
            dataset.setSegmentCount(Math.max(0, dataset.getSegmentCount() - df.getSegmentCount()));
            datasetService.updateById(dataset);
        }

        log.info("知识库文件删除成功: dfId={}, fileId={}", datasetFileId, df.getFileId());
    }

    @Override
    @Transactional
    public void reparse(Long datasetFileId) {
        DatasetFileEntity df = getById(datasetFileId);
        if (df == null) {
            throw new RuntimeException("关联记录不存在: id=" + datasetFileId);
        }

        DatasetEntity dataset = datasetService.getById(df.getDatasetId());
        if (dataset == null) {
            throw new RuntimeException("知识库不存在");
        }

        // 删除旧向量数据
        if (df.getSegmentCount() > 0) {
            try {
                List<DocumentSegmentEntity> segments = segmentMapper.selectList(
                        new LambdaQueryWrapper<DocumentSegmentEntity>()
                                .eq(DocumentSegmentEntity::getDatasetFileId, datasetFileId));
                if (!segments.isEmpty()) {
                    VectorStore vectorStore = storeManager.getVectorStore(dataset.getCollectionName());
                    List<String> segmentIds = segments.stream()
                            .map(DocumentSegmentEntity::getSegmentId)
                            .collect(Collectors.toList());
                    vectorStore.delete(segmentIds);
                    segmentMapper.delete(new LambdaQueryWrapper<DocumentSegmentEntity>()
                            .eq(DocumentSegmentEntity::getDatasetFileId, datasetFileId));
                }
            } catch (Exception e) {
                log.error("删除旧向量数据失败: dfId={}", datasetFileId, e);
            }
        }

        // 更新统计（减去旧的切片数）
        dataset.setSegmentCount(Math.max(0, dataset.getSegmentCount() - df.getSegmentCount()));
        datasetService.updateById(dataset);

        // 重置状态，重新触发向量化
        df.setStatus("PENDING");
        df.setErrorMessage(null);
        df.setSegmentCount(0);
        updateById(df);

        // 发布事件，由 @TransactionalEventListener(afterCommit) 触发异步向量化
        eventPublisher.publishEvent(new DatasetFileUploadedEvent(df.getId()));

        log.info("重新解析触发成功: dfId={}", datasetFileId);
    }

    @Override
    public List<Map<String, Object>> testSearch(Long datasetId, String query, int topK) {
        DatasetEntity dataset = datasetService.getById(datasetId);
        if (dataset == null) {
            throw new RuntimeException("知识库不存在: id=" + datasetId);
        }

        VectorStore vectorStore = storeManager.getVectorStore(dataset.getCollectionName());
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("rank", i + 1);
            item.put("content", doc.getText());
            item.put("score", doc.getScore() != null ? doc.getScore() : 0);
            item.put("metadata", doc.getMetadata());
            items.add(item);
        }
        return items;
    }

    @Override
    public void processDatasetFile(Long dfId) {
        DatasetFileEntity df = getById(dfId);
        if (df == null) return;

        try {
            df.setStatus("PROCESSING");
            updateById(df);

            DatasetEntity dataset = datasetService.getById(df.getDatasetId());
            if (dataset == null) {
                throw new RuntimeException("知识库不存在");
            }

            VectorStore vectorStore = storeManager.getVectorStore(dataset.getCollectionName());

            // 从 file_record 加载文件
            FileInfo fileInfo = fileStorageService.getById(df.getFileId());
            Resource resource = fileStorageService.loadFileAsResource(df.getFileId());

            List<Document> documents = readFile(resource, fileInfo);
            if (documents.isEmpty()) {
                throw new RuntimeException("不支持的文件类型或文件内容为空");
            }

            documents.forEach(d -> {
                d.getMetadata().put("dataset_id", df.getDatasetId());
                d.getMetadata().put("dataset_file_id", dfId);
                d.getMetadata().put("file_id", df.getFileId());
                d.getMetadata().put("source", fileInfo.getOriginalName());
            });

            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(dataset.getChunkSize())
                    .withMinChunkLengthToEmbed(20)
                    .build();
            List<Document> splits = splitter.apply(documents);

            vectorStore.add(splits);

            // 保存切片元数据（用于后续删除定位）
            for (Document split : splits) {
                DocumentSegmentEntity seg = DocumentSegmentEntity.builder()
                        .datasetFileId(dfId)
                        .segmentId(split.getId())
                        .content(split.getText())
                        .metaData(!CollectionUtils.isEmpty(split.getMetadata()) ? JacksonUtils.toJson(split.getMetadata()) : null)
                        .build();
                segmentMapper.insert(seg);
            }

            df.setStatus("COMPLETED");
            df.setSegmentCount(splits.size());
            updateById(df);

            dataset.setSegmentCount(dataset.getSegmentCount() + splits.size());
            datasetService.updateById(dataset);

            log.info("文件向量化完成: dfId={}, fileId={}, segments={}", dfId, df.getFileId(), splits.size());
        } catch (Exception e) {
            log.error("文件向量化失败: dfId={}", dfId, e);
            df.setStatus("FAILED");
            df.setErrorMessage(e.getMessage());
            updateById(df);
        }
    }

    private List<Document> readFile(Resource resource, FileInfo fileInfo) {
        String extension = fileInfo.getExtension() != null ? fileInfo.getExtension().toLowerCase() : "";
        for (FileContentReader reader : readers) {
            if (reader.supports(extension)) {
                return reader.read(resource, fileInfo);
            }
        }
        return List.of();
    }
}
