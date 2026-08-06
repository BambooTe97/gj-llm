package com.gj.llm.rag.service;

import com.gj.llm.rag.vector.DynamicVectorStoreManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Milvus 稠密检索 -- 走专业向量库(专业工具做专业事)。
 *
 * <p>当 {@code gj.llm.rag.dense.provider=milvus}(默认)时激活。
 * 向量只存 Milvus,ES 不存向量(省 ES 内存),ES 退化为纯 BM25 引擎。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "gj.llm.rag.dense", name = "provider", havingValue = "milvus", matchIfMissing = true)
public class MilvusDenseRetriever implements DenseRetriever {

    private final DynamicVectorStoreManager vectorStoreManager;

    public MilvusDenseRetriever(DynamicVectorStoreManager vectorStoreManager) {
        this.vectorStoreManager = vectorStoreManager;
    }

    @Override
    public List<Document> search(String collectionName, String query, int topK) {
        try {
            VectorStore store = vectorStoreManager.getVectorStore(collectionName);
            return store.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL)
                    .build());
        } catch (Exception e) {
            log.error("Milvus dense 检索失败: collection={}, query={}", collectionName, query, e);
            return List.of();
        }
    }
}
