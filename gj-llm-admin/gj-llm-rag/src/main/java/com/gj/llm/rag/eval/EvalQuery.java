package com.gj.llm.rag.eval;

import lombok.Data;

/**
 * 检索评测单条用例 -- 一个查询及其期望命中的判定依据。
 *
 * @author zf
 */
@Data
public class EvalQuery {

    /** 检索查询（原始查询，不走改写，直接测索引+reranker 质量） */
    private String query;

    /** 期望命中的来源文件名（与 source 元数据做包含匹配，可只填部分） */
    private String expectedSource;

    /** 期望命中的文本片段（对子块文本或父块文本做子串匹配） */
    private String expectedSnippet;
}
