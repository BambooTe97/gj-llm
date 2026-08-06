package com.gj.llm.rag.model;

import java.util.List;

/**
 * 精排测试结果 -- 包含 reranker 可用状态、精排阈值与排序后的结果项。
 *
 * <p>定位为"入库召回自检 + 线上采用预判":精排分({@link RankedTestItem#rerankScore()})
 * 是 chat 模块实际采信的分(线上 {@code rerank-score-threshold} 即卡在此分上),
 * 故页面以精排分为主、{@link RankedTestItem#coarseScore()}(Milvus 余弦)为辅。</p>
 *
 * @param rerankerAvailable     reranker 是否可用(连通性检查结果);为 false 时结果实为粗排降级
 * @param rerankScoreThreshold  线上精排采纳阈值;精排分 ≥ 此值的结果会在对话中被采用
 * @param items                 排序结果(按精排分降序)
 * @author gj-llm
 */
public record TestRankedResult(boolean rerankerAvailable, double rerankScoreThreshold, List<RankedTestItem> items) {

    public static TestRankedResult empty(boolean rerankerAvailable, double rerankScoreThreshold) {
        return new TestRankedResult(rerankerAvailable, rerankScoreThreshold, List.of());
    }
}
