package com.gj.llm.es.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ES 文档 source 字段结构 -- 替代 Map<String, Object>，明确每个属性的含义。
 *
 * <p>parent_content / parent_id 支持父子召回：检索命中子块后用 parent_content 作为 LLM 上下文。
 * 旧索引/旧文档这些字段为 null，检索侧回退用子块 content。</p>
 */
public record EsDocumentSource(
        @JsonProperty("content") String content,
        @JsonProperty("source") String source,
        @JsonProperty("dataset_id") Long datasetId,
        @JsonProperty("dataset_file_id") Long datasetFileId,
        @JsonProperty("file_id") Long fileId,
        @JsonProperty("parent_content") String parentContent,
        @JsonProperty("parent_id") String parentId
) {
}
