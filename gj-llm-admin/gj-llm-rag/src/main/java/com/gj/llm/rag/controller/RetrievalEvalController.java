package com.gj.llm.rag.controller;

import com.gj.llm.common.web.R;
import com.gj.llm.rag.entity.EvalQueryEntity;
import com.gj.llm.rag.eval.EvalQuery;
import com.gj.llm.rag.eval.RetrievalEvalResult;
import com.gj.llm.rag.eval.RetrievalEvalTask;
import com.gj.llm.rag.service.RetrievalEvalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 检索评测控制器 -- 评测用例 CRUD + 导入 + 跑评测。
 *
 * <p>从 {@code DatasetController} 拆出,单一职责,避免后者过度拥挤。
 * 评测用例按 datasetId 持久化,跑评测复现线上检索链路(hybrid + reranker,不走改写)。</p>
 *
 * <p>跑评测为异步任务化:POST /eval 立即返回 taskId,GET /eval/tasks/{taskId} 轮询进度与结果,
 * 避免用例集较大时同步阻塞超时(下游 embedding/reranker 内存有限,后台串行执行不抬高峰值)。</p>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/v1/datasets/{datasetId}")
@RequiredArgsConstructor
public class RetrievalEvalController {

    private final RetrievalEvalService retrievalEvalService;

    // ==================== 评测用例管理 ====================

    @GetMapping("/eval-queries")
    public R<List<EvalQueryEntity>> list(@PathVariable Long datasetId) {
        return R.ok(retrievalEvalService.listByDataset(datasetId));
    }

    @PostMapping("/eval-queries")
    public R<EvalQueryEntity> create(@PathVariable Long datasetId, @RequestBody EvalQuery request) {
        return R.ok(retrievalEvalService.create(datasetId, request), "评测用例新增成功");
    }

    @PutMapping("/eval-queries/{id}")
    public R<EvalQueryEntity> update(@PathVariable Long datasetId, @PathVariable Long id, @RequestBody EvalQuery request) {
        return R.ok(retrievalEvalService.update(id, request), "评测用例更新成功");
    }

    @DeleteMapping("/eval-queries/{id}")
    public R<Void> delete(@PathVariable Long datasetId, @PathVariable Long id) {
        retrievalEvalService.removeById(id);
        return R.ok(null, "评测用例删除成功");
    }

    @PostMapping("/eval-queries/import")
    public R<Integer> importQueries(@PathVariable Long datasetId, @RequestBody List<EvalQuery> queries) {
        int count = retrievalEvalService.importQueries(datasetId, queries);
        return R.ok(count, "导入 " + count + " 条评测用例");
    }

    // ==================== 跑评测(异步任务化) ====================

    /**
     * 提交异步评测任务,立即返回 taskId。
     *
     * <p>后台串行执行该库评测用例集(hybrid 检索 + rerank),逐条上报进度。
     * 前端用 {@code GET /eval/tasks/{taskId}} 轮询进度与最终结果(Recall@5 / MRR + 明细)。</p>
     */
    @PostMapping("/eval")
    public R<Map<String, String>> eval(@PathVariable Long datasetId) {
        String taskId = retrievalEvalService.submit(datasetId);
        return R.ok(Map.of("taskId", taskId), "评测任务已提交");
    }

    /**
     * 查询评测任务状态/结果(供前端轮询)。
     *
     * @return 任务状态:进行中含 done/total 进度,完成含完整 {@link RetrievalEvalResult},失败含 errorMessage
     */
    @GetMapping("/eval/tasks/{taskId}")
    public R<RetrievalEvalTask> getEvalTask(@PathVariable Long datasetId, @PathVariable String taskId) {
        RetrievalEvalTask task = retrievalEvalService.getTask(taskId);
        if (task == null) {
            throw new RuntimeException("评测任务不存在或已过期: " + taskId);
        }
        return R.ok(task);
    }
}
