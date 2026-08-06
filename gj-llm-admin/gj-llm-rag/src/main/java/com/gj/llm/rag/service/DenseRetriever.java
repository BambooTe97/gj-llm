package com.gj.llm.rag.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 稠密检索(dense)策略 -- 向量相似检索的统一出口。
 *
 * <p>由配置 {@code gj.llm.rag.dense.provider} 决定激活哪个实现:
 * milvus 走 {@code MilvusDenseRetriever},es 走 {@code EsKnnDenseRetriever}。
 * rag 的 {@link HybridSearcher} 只依赖此接口,不感知具体向量源(开闭原则)。</p>
 *
 * @author gj-llm
 */
public interface DenseRetriever {

    /**
     * 向量相似检索。
     *
     * @param collectionName 知识库集合名
     * @param query          查询文本(实现内部负责 embed)
     * @param topK           返回数量
     * @return 按 similarity 降序的文档列表,score 为相似度
     */
    List<Document> search(String collectionName, String query, int topK);
}
