package com.gj.llm.rag.vector;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gj.llm.es.service.EsSearchService;
import com.gj.llm.rag.vector.reader.FileReaderDispatcher;
import com.gj.llm.rag.vector.splitter.RecursiveCharacterTextSplitter;
import com.gj.llm.file.model.FileInfo;
import com.gj.llm.file.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FileVectorStoreHandler {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 150;
    private static final int DEFAULT_MIN_CHUNK_LENGTH = 20;

    private final FileStorageService fileStorageService;
    private final EsSearchService esSearchService;
    private final FileReaderDispatcher dispatcher;

    public FileVectorStoreHandler(FileStorageService fileStorageService,
                                  EsSearchService esSearchService,
                                  FileReaderDispatcher dispatcher) {
        this.fileStorageService = fileStorageService;
        this.esSearchService = esSearchService;
        this.dispatcher = dispatcher;
    }

    public int vectorizeAllFiles(String type, Integer chunkSize) {
        RecursiveCharacterTextSplitter splitter = buildSplitter(chunkSize);
        List<FileInfo> allFiles = fetchAllFiles();
        int count = 0;
        for (FileInfo fileInfo : allFiles) {
            try {
                Resource resource = fileStorageService.loadFileAsResource(fileInfo.getId());
                List<Document> documents = dispatcher.read(resource, fileInfo);
                if (!documents.isEmpty()) {
                    documents.forEach(doc -> {
                        doc.getMetadata().put("type", type);
                        doc.getMetadata().put("source", fileInfo.getOriginalName());
                        doc.getMetadata().put("fileId", fileInfo.getId());
                    });
                    List<Document> splits = splitter.split(documents);
                    esSearchService.indexDocuments(type, splits);
                    count++;
                }
            } catch (Exception e) {
                log.error("向量化文件失败: {} - {}", fileInfo.getOriginalName(), e.getMessage());
            }
        }
        log.info("成功向量化 {} 个文件到 ES 索引: {}", count, type);
        return count;
    }

    public void vectorizeFile(String type, Long fileId, Integer chunkSize) {
        RecursiveCharacterTextSplitter splitter = buildSplitter(chunkSize);
        FileInfo fileInfo = fileStorageService.getById(fileId);
        Resource resource = fileStorageService.loadFileAsResource(fileId);
        List<Document> documents = dispatcher.read(resource, fileInfo);
        documents.forEach(doc -> {
            doc.getMetadata().put("type", type);
            doc.getMetadata().put("source", fileInfo.getOriginalName());
            doc.getMetadata().put("fileId", fileInfo.getId());
        });
        List<Document> splits = splitter.split(documents);
        esSearchService.indexDocuments(type, splits);
        log.info("成功将文件存入 ES 索引: {}, 文件: {}", type, fileInfo.getOriginalName());
    }

    private RecursiveCharacterTextSplitter buildSplitter(Integer chunkSize) {
        int size = chunkSize != null && chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
        return new RecursiveCharacterTextSplitter(size, DEFAULT_CHUNK_OVERLAP, DEFAULT_MIN_CHUNK_LENGTH);
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
