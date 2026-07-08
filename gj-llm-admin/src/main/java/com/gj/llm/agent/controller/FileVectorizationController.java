package com.gj.llm.agent.controller;

import com.gj.llm.agent.vector.FileVectorStoreHandler;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class FileVectorizationController {

    private final FileVectorStoreHandler fileVectorStoreHandler;

    public FileVectorizationController(FileVectorStoreHandler fileVectorStoreHandler) {
        this.fileVectorStoreHandler = fileVectorStoreHandler;
    }

    /**
     * 将所有已上传文件向量化到指定类型的向量库
     */
    @PostMapping("/vectorize/all")
    public Map<String, Object> vectorizeAll(@RequestParam String type) {
        int count = fileVectorStoreHandler.vectorizeAllFiles(type);
        return Map.of("type", type, "fileCount", count, "collection", "collection_" + type);
    }

    /**
     * 将单个文件向量化到指定类型的向量库
     */
    @PostMapping("/vectorize/file/{fileId}")
    public Map<String, Object> vectorizeFile(@RequestParam String type, @PathVariable Long fileId) {
        fileVectorStoreHandler.vectorizeFile(type, fileId);
        return Map.of("type", type, "fileId", fileId, "collection", "collection_" + type);
    }
}
