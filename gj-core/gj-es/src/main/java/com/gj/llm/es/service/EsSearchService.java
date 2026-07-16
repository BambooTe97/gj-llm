package com.gj.llm.es.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import com.gj.llm.es.config.EsProperties;
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

    private static final String BGE_QUERY_INSTRUCTION = "为这个句子生成表示以用于检索相关文章：";
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
        try {
            String indexName = toIndexName(collectionName);
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (exists) {
                log.info("ES 索引已存在: {}", indexName);
                return;
            }

            String mappingJson = buildIndexMappingJson();
            try (InputStream is = new ByteArrayInputStream(mappingJson.getBytes(StandardCharsets.UTF_8))) {
                client.indices().create(CreateIndexRequest.of(c -> c
                        .index(indexName)
                        .withJson(is)));
            }
            log.info("ES 索引创建成功: {}, dims={}", indexName, esProperties.getEmbeddingDimension());
        } catch (Exception e) {
            log.error("ES 索引创建失败: {}", collectionName, e);
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
                "similarity": "cosine"
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

    /**
     * 混合检索：BM25 倒排 + KNN 向量 + RRF 融合。
     */
    public List<Document> hybridSearch(String collectionName, String query, int topK) {
        String indexName = toIndexName(collectionName);
        int candidateK = topK * 5;

        try {
            // 1. 嵌入查询向量（加 BGE 指令前缀）
            String denseQuery = BGE_QUERY_INSTRUCTION + query;
            float[] queryVector = embeddingModel.embed(denseQuery);

            // 2. 构建 hybrid retriever JSON
            String searchJson = buildHybridSearchJson(query, queryVector, candidateK, topK);
            try (InputStream is = new ByteArrayInputStream(searchJson.getBytes(StandardCharsets.UTF_8))) {
                SearchRequest searchReq = SearchRequest.of(s -> s
                        .index(indexName)
                        .withJson(is));
                SearchResponse<Map> response = client.search(searchReq, Map.class);

                List<Document> results = new ArrayList<>();
                for (var hit : response.hits().hits()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> source = (Map<String, Object>) hit.source();
                    if (source == null) continue;

                    Document doc = new Document((String) source.getOrDefault("content", ""));
                    doc.getMetadata().put("score", hit.score() != null ? hit.score() : 0);
                    doc.getMetadata().put("source", source.getOrDefault("source", ""));
                    doc.getMetadata().put("dataset_id", source.get("dataset_id"));
                    doc.getMetadata().put("dataset_file_id", source.get("dataset_file_id"));
                    results.add(doc);
                }

                log.info("ES 混合检索: index={}, query={}, candidates={}, results={}",
                        indexName, query.substring(0, Math.min(query.length(), 50)),
                        response.hits().total() != null ? response.hits().total().value() : 0, results.size());

                return results;
            }
        } catch (Exception e) {
            log.error("ES 混合检索失败: index={}, query={}", indexName, query, e);
            return List.of();
        }
    }

    private String buildHybridSearchJson(String queryText, float[] queryVector, int candidateK, int topK) {
        // 使用 ES 8.15 retriever API: BM25 standard retriever + KNN retriever → RRF
        return """
        {
          "retriever": {
            "rrf": {
              "retrievers": [
                {
                  "standard": {
                    "query": { "match": { "content": "%s" } }
                  }
                },
                {
                  "knn": {
                    "field": "embedding",
                    "query_vector": %s,
                    "k": %d,
                    "num_candidates": %d
                  }
                }
              ],
              "rank_constant": 60,
              "rank_window_size": %d
            }
          },
          "size": %d
        }
        """.formatted(escapeJson(queryText), toJsonArray(queryVector), candidateK, candidateK * 2, candidateK, topK);
    }

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
