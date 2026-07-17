package com.gj.llm.es.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ES 文档 source 字段结构 —— 替代 Map<String, Object>，明确每个属性的含义。
 */
public record EsDocumentSource(
        @JsonProperty("content") String content,
        @JsonProperty("source") String source,
        @JsonProperty("dataset_id") Long datasetId,
        @JsonProperty("dataset_file_id") Long datasetFileId
) {}
