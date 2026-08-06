package com.gj.llm.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.rag.entity.EvalQueryEntity;
import com.gj.llm.rag.eval.EvalQuery;
import com.gj.llm.rag.eval.RetrievalEvalResult;
import com.gj.llm.rag.eval.RetrievalEvalTask;

import java.util.List;

/**
 * 检索评测服务 -- 评测用例持久化(CRUD + 导入) + 跑评测(委托 {@link com.gj.llm.rag.eval.RetrievalEvaluator})。
 *
 * @author gj-llm
 */
public interface RetrievalEvalService extends IService<EvalQueryEntity> {

    /** 按 datasetId 列出评测用例 */
    List<EvalQueryEntity> listByDataset(Long datasetId);

    /** 新增评测用例 */
    EvalQueryEntity create(Long datasetId, EvalQuery request);

    /** 修改评测用例 */
    EvalQueryEntity update(Long id, EvalQuery request);

    /** 批量导入评测用例（外部 AI 按模板生成后灌入） */
    int importQueries(Long datasetId, List<EvalQuery> queries);

    /** 跑评测（同步）：加载该库全部用例 -> 委托 RetrievalEvaluator 计算 Recall@K / MRR */
    RetrievalEvalResult run(Long datasetId);

    /** 提交异步评测任务，返回 taskId（接口立即返回，后台串行执行，不抬高下游峰值） */
    String submit(Long datasetId);

    /** 查询评测任务状态/结果（供前端轮询；任务不存在或已过期返回 null） */
    RetrievalEvalTask getTask(String taskId);
}
