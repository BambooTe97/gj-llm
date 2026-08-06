package com.gj.llm.rag.service;

import com.gj.llm.es.service.EsSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合检索器 -- ES BM25(稀疏)+ {@link DenseRetriever}(稠密)+ Java 侧 RRF 融合。
 *
 * <p>融合挪到 rag、按文本去重:因为 BM25(ES doc id)与 Milvus(doc_id)不是同一套 id,
 * 按 id 融合会失效,必须按文本对齐(与 RetrievalService 跨变体合并的 key 一致)。</p>
 *
 * <p>展示分用 dense 的余弦相似度(与线上对话检索一致);仅 BM25 命中的片段展示分为 0。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearcher {

    private static final double RRF_K = 60.0;
    private static final double SPARSE_WEIGHT = 0.3;
    private static final double DENSE_WEIGHT = 0.7;

    private final EsSearchService esSearchService;
    private final DenseRetriever denseRetriever;

    /**
     * 混合检索。
     *
     * @param collectionName 知识库集合名
     * @param query          查询文本
     * @param topK           最终返回数量(粗排候选池为 topK*5)
     * @return RRF 融合后的文档列表,按融合分降序,score 为 dense 余弦相似度
     */
    public List<Document> search(String collectionName, String query, int topK) {
        int candidateK = topK * 5;
        List<Document> bm25Hits = esSearchService.bm25SearchDocs(collectionName, query, candidateK);
        List<Document> denseHits = denseRetriever.search(collectionName, query, candidateK);

        // RRF 融合(按文本 key)+ 收集 dense 余弦分(展示用)
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, Document> docByText = new LinkedHashMap<>();
        Map<String, Double> denseScore = new LinkedHashMap<>();

        for (int i = 0; i < bm25Hits.size(); i++) {
            Document d = bm25Hits.get(i);
            String key = d.getText().trim();
            rrfScores.merge(key, SPARSE_WEIGHT / (RRF_K + i + 1), Double::sum);
            docByText.putIfAbsent(key, d);
        }
        for (int i = 0; i < denseHits.size(); i++) {
            Document d = denseHits.get(i);
            String key = d.getText().trim();
            rrfScores.merge(key, DENSE_WEIGHT / (RRF_K + i + 1), Double::sum);
            docByText.putIfAbsent(key, d);
            denseScore.putIfAbsent(key, d.getScore() != null ? d.getScore() : 0.0);
        }

        List<Document> results = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    Document d = docByText.get(e.getKey());
                    double display = denseScore.getOrDefault(e.getKey(), 0.0);
                    return Document.builder().text(d.getText()).score(display).metadata(d.getMetadata()).build();
                })
                .toList();

        log.info("Hybrid 检索: collection={}, query={}, bm25={}, dense={}, merged={}",
                collectionName, query.substring(0, Math.min(query.length(), 50)),
                bm25Hits.size(), denseHits.size(), results.size());
        return results;
    }
}
