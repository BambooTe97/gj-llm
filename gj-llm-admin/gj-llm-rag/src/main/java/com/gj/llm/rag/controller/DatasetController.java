package com.gj.llm.rag.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.entity.DatasetFileEntity;
import com.gj.llm.rag.model.DatasetCreateRequest;
import com.gj.llm.rag.model.DatasetFileVO;
import com.gj.llm.rag.model.DatasetUpdateRequest;
import com.gj.llm.rag.model.SearchResultItem;
import com.gj.llm.rag.model.TestSearchRequest;
import com.gj.llm.rag.service.DatasetFileService;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;
    private final DatasetFileService datasetFileService;

    // ==================== 知识库 CRUD ====================

    @GetMapping
    public ApiResponse<IPage<DatasetEntity>> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(datasetService.page(page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<DatasetEntity> get(@PathVariable Long id) {
        return ApiResponse.ok(datasetService.getById(id));
    }

    @PostMapping
    public ApiResponse<DatasetEntity> create(@Valid @RequestBody DatasetCreateRequest request) {
        return ApiResponse.ok(datasetService.create(request), "知识库创建成功");
    }

    @PutMapping("/{id}")
    public ApiResponse<DatasetEntity> update(@PathVariable Long id, @Valid @RequestBody DatasetUpdateRequest request) {
        return ApiResponse.ok(datasetService.update(id, request), "知识库更新成功");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        datasetService.delete(id);
        return ApiResponse.ok(null, "知识库删除成功");
    }

    // ==================== 文档管理 ====================

    @PostMapping("/{datasetId}/documents/upload")
    public ApiResponse<DatasetFileEntity> uploadDocument(@PathVariable Long datasetId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(datasetFileService.upload(datasetId, file), "文件上传成功");
    }

    @GetMapping("/{datasetId}/documents")
    public ApiResponse<IPage<DatasetFileVO>> listDocuments(@PathVariable Long datasetId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(datasetFileService.pageByDataset(datasetId, page, pageSize));
    }

    @DeleteMapping("/{datasetId}/documents/{dfId}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long datasetId, @PathVariable Long dfId) {
        datasetFileService.delete(dfId);
        return ApiResponse.ok(null, "文件删除成功");
    }

    @PostMapping("/{datasetId}/documents/{dfId}/reparse")
    public ApiResponse<Void> reparseDocument(@PathVariable Long datasetId, @PathVariable Long dfId) {
        datasetFileService.reparse(dfId);
        return ApiResponse.ok(null, "已触发重新解析");
    }

    // ==================== 检索测试 ====================

    @PostMapping("/{datasetId}/test")
    public ApiResponse<List<SearchResultItem>> testSearch(@PathVariable Long datasetId, @Valid @RequestBody TestSearchRequest request) {
        return ApiResponse.ok(datasetFileService.testSearch(datasetId, request.getQuery(), request.getTopK()));
    }
}
