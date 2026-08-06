package com.gj.llm.rag.service;

import com.gj.llm.es.service.EsSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ES KNN 稠密检索 -- 向量走 ES(ES 同时做 BM25 + KNN)。
 *
 * <p>当 {@code gj.llm.rag.dense.provider=es} 时激活。
 * 保留此实现作为备选部署(代码两种都保留),向量需存 ES。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "gj.llm.rag.dense", name = "provider", havingValue = "es")
public class EsKnnDenseRetriever implements DenseRetriever {

    private final EsSearchService esSearchService;

    public EsKnnDenseRetriever(EsSearchService esSearchService) {
        this.esSearchService = esSearchService;
    }

    @Override
    public List<Document> search(String collectionName, String query, int topK) {
        return esSearchService.knnSearchDocs(collectionName, query, topK);
    }
}
