package com.gj.llm.es.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.es.config.EsProperties;
import com.gj.llm.es.model.EsDocumentSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Elasticsearch 统一检索服务 —— 倒排索引（BM25）+ 稠密向量（KNN）混合检索。
 *
 * <p>替代原来的 Milvus + 内存 Lucene 方案。</p>
 */
@Slf4j
@Service
public class EsSearchService {

    private static final int BATCH_SIZE = 20;

    private final ElasticsearchClient client;
    private final EmbeddingModel embeddingModel;
    private final EsProperties esProperties;

    public EsSearchService(ElasticsearchClient client,
                           EmbeddingModel embeddingModel,
                           EsProperties esProperties) {
        this.client = client;
        this.embeddingModel = embeddingModel;
        this.esProperties = esProperties;
    }

    // ==================== 索引管理 ====================

    public void ensureIndexExists(String collectionName) {
        String indexName = toIndexName(collectionName);
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (exists) {
                return;
            }
        } catch (Exception e) {
            // exists 检查失败，直接尝试创建
        }

        try {
            String mappingJson = buildIndexMappingJson();
            try (InputStream is = new ByteArrayInputStream(mappingJson.getBytes(StandardCharsets.UTF_8))) {
                client.indices().create(CreateIndexRequest.of(c -> c
                        .index(indexName)
                        .withJson(is)));
            }
            log.info("ES 索引创建成功: {}", indexName);
        } catch (Exception e) {
            // 并发场景下索引可能已由其他线程创建，忽略
            if (e.getMessage() != null && e.getMessage().contains("already_exists")) {
                log.info("ES 索引已存在（并发创建）: {}", indexName);
            } else {
                log.error("ES 索引创建失败: {}", indexName, e);
            }
        }
    }

    private String buildIndexMappingJson() {
        int dims = esProperties.getEmbeddingDimension();
        int shards = esProperties.getShards();
        int replicas = esProperties.getReplicas();
        return """
        {
          "settings": {
            "number_of_shards": %d,
            "number_of_replicas": %d,
            "analysis": {
              "analyzer": {
                "ik_max_word_analyzer": { "type": "ik_max_word" }
              }
            }
          },
          "mappings": {
            "properties": {
              "content": {
                "type": "text",
                "analyzer": "ik_max_word",
                "search_analyzer": "ik_smart"
              },
              "embedding": {
                "type": "dense_vector",
                "dims": %d,
                "index": true,
                "similarity": "cosine",
                "index_options": {
                  "type": "hnsw",
                  "m": 32,
                  "ef_construction": 200
                }
              },
              "dataset_id":    { "type": "long" },
              "dataset_file_id": { "type": "long" },
              "file_id":       { "type": "long" },
              "source":        { "type": "keyword" },
              "metadata":      { "type": "object", "enabled": false }
            }
          }
        }
        """.formatted(shards, replicas, dims);
    }

    // ==================== 文档 CRUD ====================

    /** 批量 embed 并写入索引 */
    public void indexDocuments(String collectionName, List<Document> docs) {
        if (docs.isEmpty()) return;
        String indexName = toIndexName(collectionName);
        ensureIndexExists(collectionName);

        try {
            // 分批 embed + index
            for (int start = 0; start < docs.size(); start += BATCH_SIZE) {
                int end = Math.min(start + BATCH_SIZE, docs.size());
                List<Document> batch = docs.subList(start, end);

                // 批量嵌入
                List<String> texts = batch.stream().map(Document::getText).toList();
                List<float[]> embeddings = embeddingModel.embed(texts);

                // 构建 bulk 请求
                BulkRequest.Builder bulkBuilder = new BulkRequest.Builder().index(indexName);
                for (int i = 0; i < batch.size(); i++) {
                    Document doc = batch.get(i);
                    float[] embedding = embeddings.get(i);

                    Map<String, Object> source = new LinkedHashMap<>();
                    source.put("content", doc.getText());
                    source.put("embedding", embedding);
                    source.put("dataset_id", doc.getMetadata().get("dataset_id"));
                    source.put("dataset_file_id", doc.getMetadata().get("dataset_file_id"));
                    source.put("file_id", doc.getMetadata().get("file_id"));
                    source.put("source", doc.getMetadata().get("source"));
                    source.put("metadata", doc.getMetadata());

                    int idx = i;
                    bulkBuilder.operations(op -> op
                            .index(ix -> ix
                                    .id(doc.getId())
                                    .document(source)));
                }

                BulkResponse bulkResp = client.bulk(bulkBuilder.build());
                if (bulkResp.errors()) {
                    for (BulkResponseItem item : bulkResp.items()) {
                        if (item.error() != null) {
                            log.warn("ES bulk 写入失败: id={}, error={}", item.id(), item.error().reason());
                        }
                    }
                }
            }
            log.info("ES 索引写入完成: index={}, count={}", indexName, docs.size());
        } catch (Exception e) {
            log.error("ES 索引写入失败: index={}", indexName, e);
        }
    }

    /** 按 segmentId 批量删除 */
    public void deleteDocuments(String collectionName, List<String> segmentIds) {
        if (segmentIds.isEmpty()) return;
        String indexName = toIndexName(collectionName);
        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder().index(indexName);
            for (String id : segmentIds) {
                bulkBuilder.operations(op -> op.delete(d -> d.id(id)));
            }
            client.bulk(bulkBuilder.build());
            log.info("ES 文档删除: index={}, count={}", indexName, segmentIds.size());
        } catch (Exception e) {
            log.error("ES 文档删除失败: index={}", indexName, e);
        }
    }

    /** 删除整个索引 */
    public void deleteIndex(String collectionName) {
        String indexName = toIndexName(collectionName);
        try {
            client.indices().delete(DeleteIndexRequest.of(d -> d.index(indexName)));
            log.info("ES 索引删除: {}", indexName);
        } catch (Exception e) {
            log.warn("ES 索引删除失败（可能不存在）: {}", indexName);
        }
    }

    // ==================== 混合检索 ====================

    private static final double RRF_K = 60.0;
    private static final double SPARSE_WEIGHT = 0.3;
    private static final double DENSE_WEIGHT = 0.7;

    /**
     * 混合检索：BM25 倒排 + KNN 向量，Java 侧 RRF 融合（ES 9.x 免费版不支持内置 RRF）。
     */
    public List<Document> hybridSearch(String collectionName, String query, int topK) {
        String indexName = toIndexName(collectionName);
        int candidateK = topK * 5;

        try {
            // 1. 嵌入查询向量
            float[] queryVector = embeddingModel.embed(query);

            // 2. 分别执行 BM25 和 KNN 查询
            List<Hit> bm25Hits = bm25Search(indexName, query, candidateK);
            List<Hit> knnHits = knnSearch(indexName, queryVector, candidateK);

            // 3. Java 侧 RRF 融合（仅排序用）+ 收集 KNN 余弦相似度（展示用）
            Map<String, Double> rrfScores = new LinkedHashMap<>();
            Map<String, EsDocumentSource> sourceMap = new LinkedHashMap<>();
            Map<String, Double> knnScoreMap = new LinkedHashMap<>();

            for (int i = 0; i < bm25Hits.size(); i++) {
                Hit hit = bm25Hits.get(i);
                rrfScores.merge(hit.id(), SPARSE_WEIGHT / (RRF_K + i + 1), Double::sum);
                sourceMap.putIfAbsent(hit.id(), hit.source());
            }
            for (int i = 0; i < knnHits.size(); i++) {
                Hit hit = knnHits.get(i);
                rrfScores.merge(hit.id(), DENSE_WEIGHT / (RRF_K + i + 1), Double::sum);
                sourceMap.putIfAbsent(hit.id(), hit.source());
                knnScoreMap.putIfAbsent(hit.id(), hit.score());
            }

            // 4. 按 RRF 排序，展示分用 KNN 余弦相似度
            List<Document> results = rrfScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(topK)
                    .map(e -> {
                        EsDocumentSource src = sourceMap.get(e.getKey());
                        double displayScore = knnScoreMap.getOrDefault(e.getKey(), 0.0);
                        Map<String, Object> meta = new HashMap<>();
                        meta.put("source", src.source());
                        meta.put("dataset_id", src.datasetId());
                        meta.put("dataset_file_id", src.datasetFileId());
                        return Document.builder()
                                .text(src.content())
                                .score(displayScore)
                                .metadata(meta)
                                .build();
                    })
                    .toList();

            log.info("ES 混合检索: index={}, query={}, bm25={}, knn={}, merged={}",
                    indexName, query.substring(0, Math.min(query.length(), 50)),
                    bm25Hits.size(), knnHits.size(), results.size());

            return results;
        } catch (Exception e) {
            log.error("ES 混合检索失败: index={}, query={}", indexName, query, e);
            return List.of();
        }
    }

    private List<Hit> bm25Search(String indexName, String query, int size) throws Exception {
        String searchJson = """
        {
          "query": { "match": { "content": "%s" } },
          "size": %d
        }
        """.formatted(escapeJson(query), size);

        try (InputStream is = new ByteArrayInputStream(searchJson.getBytes(StandardCharsets.UTF_8))) {
            SearchRequest req = SearchRequest.of(s -> s.index(indexName).withJson(is));
            SearchResponse<Map> resp = client.search(req, Map.class);
            return resp.hits().hits().stream()
                    .map(h -> new Hit(h.id(), toSource(h.source()), h.score() != null ? h.score() : 0))
                    .toList();
        }
    }

    private List<Hit> knnSearch(String indexName, float[] queryVector, int size) throws Exception {
        String searchJson = """
        {
          "knn": {
            "field": "embedding",
            "query_vector": %s,
            "k": %d,
            "num_candidates": %d
          },
          "size": %d
        }
        """.formatted(toJsonArray(queryVector), size, size * 10, size);

        try (InputStream is = new ByteArrayInputStream(searchJson.getBytes(StandardCharsets.UTF_8))) {
            SearchRequest req = SearchRequest.of(s -> s.index(indexName).withJson(is));
            SearchResponse<Map> resp = client.search(req, Map.class);
            return resp.hits().hits().stream()
                    .map(h -> new Hit(h.id(), toSource(h.source()), h.score() != null ? h.score() : 0))
                    .toList();
        }
    }

    @SuppressWarnings("unchecked")
    private EsDocumentSource toSource(Object rawSource) {
        if (rawSource instanceof Map map) {
            return JacksonUtils.fromMap(map, EsDocumentSource.class);
        }
        return new EsDocumentSource("", "", null, null);
    }

    private record Hit(String id, EsDocumentSource source, double score) {}

    private String toJsonArray(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== 工具方法 ====================

    /** collectionName → ES 索引名（小写 + 前缀） */
    private String toIndexName(String collectionName) {
        return (esProperties.getIndexPrefix() + collectionName).toLowerCase();
    }
}
