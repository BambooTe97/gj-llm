package com.gj.llm.rag.controller;

import com.gj.llm.rag.constant.VectorStoreConstants;
import com.gj.llm.rag.vector.FileVectorStoreHandler;
import com.gj.llm.common.web.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class FileVectorizationController {

    private final FileVectorStoreHandler fileVectorStoreHandler;

    public FileVectorizationController(FileVectorStoreHandler fileVectorStoreHandler) {
        this.fileVectorStoreHandler = fileVectorStoreHandler;
    }

    @PostMapping("/vectorize/all")
    public ApiResponse<Map<String, Object>> vectorizeAll(@RequestParam String type) {
        int count = fileVectorStoreHandler.vectorizeAllFiles(type);
        Map<String, Object> data = Map.of(
                "type", type,
                "fileCount", count,
                "collection", VectorStoreConstants.COLLECTION_PREFIX + type);
        return ApiResponse.ok(data);
    }

    @PostMapping("/vectorize/file/{fileId}")
    public ApiResponse<Map<String, Object>> vectorizeFile(@RequestParam String type,
                                                           @PathVariable Long fileId) {
        fileVectorStoreHandler.vectorizeFile(type, fileId);
        Map<String, Object> data = Map.of(
                "type", type,
                "fileId", fileId,
                "collection", VectorStoreConstants.COLLECTION_PREFIX + type);
        return ApiResponse.ok(data);
    }
}
