package com.gj.llm.rag.service;

/**
 * 检索结果 -- {@link RetrievalService} 对外返回的统一结构。
 *
 * <p>把"上下文拼接、引用片段、是否无可靠结果"三件事打包,供对话层消费,
 * 检索的内部编排(改写/多路召回/精排/去重/护栏)不泄漏到对话层。</p>
 *
 * @param context           拼接好的参考上下文(已按字符预算截断),无检索时为空串
 * @param references        引用片段列表(已按分数降序、去重、过阈值),无检索时为空
 * @param noConfidentResult 是否无可靠结果(检索到内容但低于 rerank 阈值),为 true 时对话层应走"我不知道"分支
 * @author gj-llm
 */
public record RetrievalResult(String context, java.util.List<Reference> references, boolean noConfidentResult) {

    /** 空结果:无数据集 / 数据集不存在 / 检索异常时返回,对话层按通用对话处理。 */
    public static RetrievalResult empty() {
        return new RetrievalResult("", java.util.List.of(), false);
    }
}
