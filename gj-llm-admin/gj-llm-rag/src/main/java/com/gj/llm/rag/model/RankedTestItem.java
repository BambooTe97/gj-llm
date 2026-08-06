package com.gj.llm.rag.model;

/**
 * 精排测试结果项 -- 同时携带粗排分(ES 混合检索)与精排分(reranker Cross-Encoder),
 * 用于在检索测试页面对照评估排序效果。
 *
 * @param rank          精排后的排名(从 1 开始,按 rerankScore 降序)
 * @param content       文档片段正文
 * @param coarseScore   ES 粗排分(BM25 + KNN + RRF 融合)
 * @param rerankScore   reranker 精排分(Cross-Encoder)
 * @param source        来源文件名
 * @param datasetFileId 所属知识库文件 ID
 * @author gj-llm
 */
public record RankedTestItem(
        int rank,
        String content,
        double coarseScore,
        double rerankScore,
        String source,
        Object datasetFileId
) {
}
