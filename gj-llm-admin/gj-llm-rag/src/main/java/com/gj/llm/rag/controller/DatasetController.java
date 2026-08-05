package com.gj.llm.rag.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.entity.DatasetFileEntity;
import com.gj.llm.rag.eval.EvalQuery;
import com.gj.llm.rag.eval.RetrievalEvalResult;
import com.gj.llm.rag.eval.RetrievalEvaluator;
import com.gj.llm.rag.model.DatasetCreateRequest;
import com.gj.llm.rag.model.DatasetFileVO;
import com.gj.llm.rag.model.DatasetUpdateRequest;
import com.gj.llm.rag.model.SearchResultItem;
import com.gj.llm.rag.model.TestSearchRequest;
import com.gj.llm.rag.service.DatasetFileService;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.common.web.R;
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
    private final RetrievalEvaluator retrievalEvaluator;

    // ==================== 知识库 CRUD ====================

    @GetMapping
    public R<IPage<DatasetEntity>> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(datasetService.page(page, pageSize));
    }

    @GetMapping("/{id}")
    public R<DatasetEntity> get(@PathVariable Long id) {
        return R.ok(datasetService.getById(id));
    }

    @PostMapping
    public R<DatasetEntity> create(@Valid @RequestBody DatasetCreateRequest request) {
        return R.ok(datasetService.create(request), "知识库创建成功");
    }

    @PutMapping("/{id}")
    public R<DatasetEntity> update(@PathVariable Long id, @Valid @RequestBody DatasetUpdateRequest request) {
        return R.ok(datasetService.update(id, request), "知识库更新成功");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        datasetService.delete(id);
        return R.ok(null, "知识库删除成功");
    }

    // ==================== 文档管理 ====================

    @PostMapping("/{datasetId}/documents/upload")
    public R<DatasetFileEntity> uploadDocument(@PathVariable Long datasetId, @RequestParam("file") MultipartFile file) {
        return R.ok(datasetFileService.upload(datasetId, file), "文件上传成功");
    }

    @GetMapping("/{datasetId}/documents")
    public R<IPage<DatasetFileVO>> listDocuments(@PathVariable Long datasetId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(datasetFileService.pageByDataset(datasetId, page, pageSize));
    }

    @DeleteMapping("/{datasetId}/documents/{dfId}")
    public R<Void> deleteDocument(@PathVariable Long datasetId, @PathVariable Long dfId) {
        datasetFileService.delete(dfId);
        return R.ok(null, "文件删除成功");
    }

    @PostMapping("/{datasetId}/documents/{dfId}/reparse")
    public R<Void> reparseDocument(@PathVariable Long datasetId, @PathVariable Long dfId) {
        datasetFileService.reparse(dfId);
        return R.ok(null, "已触发重新解析");
    }

    // ==================== 检索测试 ====================

    @PostMapping("/{datasetId}/test")
    public R<List<SearchResultItem>> testSearch(@PathVariable Long datasetId, @Valid @RequestBody TestSearchRequest request) {
        return R.ok(datasetFileService.testSearch(datasetId, request.getQuery(), request.getTopK()));
    }

    // ==================== 离线检索评测 ====================

    /**
     * 检索评测：提交一批评测查询，返回 Recall@5 / MRR 等指标，量化衡量切分与检索改动效果。
     * 请求体为 EvalQuery 列表（见 resources/eval-queries-sample.json 示例）。
     */
    @PostMapping("/{datasetId}/eval")
    public R<RetrievalEvalResult> eval(@PathVariable Long datasetId, @RequestBody List<EvalQuery> queries) {
        return R.ok(retrievalEvaluator.evaluate(datasetId, queries), "检索评测完成");
    }
}
