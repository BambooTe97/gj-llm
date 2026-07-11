package com.gj.llm.rag.vector;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gj.llm.rag.constant.VectorStoreConstants;
import com.gj.llm.rag.vector.reader.FileContentReader;
import com.gj.llm.file.model.FileInfo;
import com.gj.llm.file.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FileVectorStoreHandler {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_MIN_CHUNK_LENGTH = 20;

    private final FileStorageService fileStorageService;
    private final DynamicVectorStoreManager storeManager;
    private final List<FileContentReader> readers;

    public FileVectorStoreHandler(FileStorageService fileStorageService, DynamicVectorStoreManager storeManager, List<FileContentReader> readers) {
        this.fileStorageService = fileStorageService;
        this.storeManager = storeManager;
        this.readers = readers;
    }

    /**
     * 将已上传的所有文件向量化并存入指定类型的向量库。
     *
     * @param type      向量库类型（如 "medical", "story"）
     * @param chunkSize 切片大小（token 数），传 null 使用默认 800
     * @return 处理的文件数量
     */
    public int vectorizeAllFiles(String type, Integer chunkSize) {
        VectorStore vectorStore = storeManager.getVectorStore(type);
        TokenTextSplitter splitter = buildSplitter(chunkSize);
        List<FileInfo> allFiles = fetchAllFiles();
        int count = 0;
        for (FileInfo fileInfo : allFiles) {
            try {
                Resource resource = fileStorageService.loadFileAsResource(fileInfo.getId());
                List<Document> documents = readFile(resource, fileInfo);
                if (!documents.isEmpty()) {
                    documents.forEach(doc -> {
                        doc.getMetadata().put("type", type);
                        doc.getMetadata().put("source", fileInfo.getOriginalName());
                        doc.getMetadata().put("fileId", fileInfo.getId());
                    });
                    List<Document> splits = splitter.apply(documents);
                    vectorStore.add(splits);
                    count++;
                }
            } catch (Exception e) {
                log.error("向量化文件失败: {} - {}", fileInfo.getOriginalName(), e.getMessage());
            }
        }
        log.info("成功向量化 {} 个文件到集合: {}{}", count, VectorStoreConstants.COLLECTION_PREFIX, type);
        return count;
    }

    /**
     * 向量化单个已上传的文件。
     *
     * @param type      向量库类型
     * @param fileId    文件记录 ID
     * @param chunkSize 切片大小（token 数），传 null 使用默认 800
     */
    public void vectorizeFile(String type, Long fileId, Integer chunkSize) {
        VectorStore vectorStore = storeManager.getVectorStore(type);
        TokenTextSplitter splitter = buildSplitter(chunkSize);
        FileInfo fileInfo = fileStorageService.getById(fileId);
        Resource resource = fileStorageService.loadFileAsResource(fileId);
        List<Document> documents = readFile(resource, fileInfo);
        documents.forEach(doc -> {
            doc.getMetadata().put("type", type);
            doc.getMetadata().put("source", fileInfo.getOriginalName());
            doc.getMetadata().put("fileId", fileInfo.getId());
        });
        List<Document> splits = splitter.apply(documents);
        vectorStore.add(splits);
        log.info("成功将文件存入集合: {}{}, 文件: {}", VectorStoreConstants.COLLECTION_PREFIX, type, fileInfo.getOriginalName());
    }

    private TokenTextSplitter buildSplitter(Integer chunkSize) {
        int size = chunkSize != null && chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
        return TokenTextSplitter.builder()
                .withChunkSize(size)
                .withMinChunkLengthToEmbed(DEFAULT_MIN_CHUNK_LENGTH)
                .build();
    }

    /**
     * 根据扩展名匹配对应的 FileContentReader 策略来读取文件
     */
    private List<Document> readFile(Resource resource, FileInfo fileInfo) {
        String extension = fileInfo.getExtension() != null ? fileInfo.getExtension().toLowerCase() : "";
        for (FileContentReader reader : readers) {
            if (reader.supports(extension)) {
                return reader.read(resource, fileInfo);
            }
        }
        log.info("不支持的文件类型: .{} ({})", extension, fileInfo.getOriginalName());
        return List.of();
    }

    private List<FileInfo> fetchAllFiles() {
        List<FileInfo> allFiles = new ArrayList<>();
        int page = 1;
        int pageSize = 50;
        Page<FileInfo> result;
        do {
            result = fileStorageService.listFiles(page, pageSize);
            allFiles.addAll(result.getRecords());
            page++;
        } while (page * pageSize < result.getTotal());
        return allFiles;
    }
}
