package com.gj.llm.rag.model;

import java.util.Map;

/**
 * 检索结果项 —— 替代 Map<String, Object>，明确返回给前端的字段。
 */
public record SearchResultItem(
        int rank,
        String content,
        double score,
        Map<String, Object> metadata
) {}
