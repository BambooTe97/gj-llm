package com.gj.llm.rag.eval;

import lombok.Data;

import java.util.List;

/**
 * 检索评测结果 -- 聚合指标 + 每条查询明细。
 *
 * @author zf
 */
@Data
public class RetrievalEvalResult {

    /** 评测查询总数 */
    private int total;

    /** Recall@K：期望命中出现在 top-K 的查询占比 */
    private double recallAtK;

    /** MRR：平均倒数排名（1/rank 的均值，未命中记 0） */
    private double mrr;

    /** 当前配置的精排采纳阈值（rerank-score-threshold），供前端阈值扫描标记"当前"位置 */
    private double rerankScoreThreshold;

    /** 每条查询的明细 */
    private List<Item> items;

    @Data
    public static class Item {
        private String query;
        /** 是否命中 */
        private boolean found;
        /** 首次命中的排名（1-based，0 表示未命中） */
        private int rank;
        /** top-1 的 rerank 分数 */
        private double topScore;
        /** 期望文档的精排分（未命中记 0）；用于前端客户端阈值扫描：effective recall(T) = 命中且 expectedScore ≥ T 的占比 */
        private double expectedScore;
    }
}
