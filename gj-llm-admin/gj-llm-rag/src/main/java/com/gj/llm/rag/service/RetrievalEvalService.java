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

    /** 提交异步评测任务（接口立即返回，后台串行执行，不抬高下游峰值）。
     *  queryIds 为空=跑该库全部用例，非空=只跑指定用例（选择性测评）。
     *  任务按 datasetId 存 Redis(一个库一个最近任务槽位),前端用 getTaskByDataset 轮询/恢复 */
    void submit(Long datasetId, List<Long> queryIds);

    /** 查询该库最近一次评测任务状态/结果（供前端轮询与重进恢复；不存在或已过期返回 null） */
    RetrievalEvalTask getTaskByDataset(Long datasetId);
}
