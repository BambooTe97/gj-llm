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
              "parent_id":     { "type": "keyword" },
              "parent_content": { "type": "text", "index": false },
              "chunk_index":   { "type": "integer" },
              "metadata":      { "type": "object", "enabled": false }
            }
          }
        }
        """.formatted(shards, replicas, dims);
    }

    // ==================== 文档 CRUD ====================

    /** 批量 embed 并写入索引(默认写 embedding,ES 做 KNN) */
    public void indexDocuments(String collectionName, List<Document> docs) {
        indexDocuments(collectionName, docs, true);
    }

    /**
     * 批量写入 ES 索引。
     *
     * @param writeEmbedding true=嵌入并写 embedding 字段(ES 做 KNN);
     *                       false=只写文本+元数据,不写向量(向量交 Milvus,ES 瘦身省内存)
     */
    public void indexDocuments(String collectionName, List<Document> docs, boolean writeEmbedding) {
        if (docs.isEmpty()) return;
        String indexName = toIndexName(collectionName);
        ensureIndexExists(collectionName);

        try {
            // 分批 embed + index
            for (int start = 0; start < docs.size(); start += BATCH_SIZE) {
                int end = Math.min(start + BATCH_SIZE, docs.size());
                List<Document> batch = docs.subList(start, end);

                // 批量嵌入(仅当需要写 embedding 时;否则不调 embed,省时省内存)
                List<float[]> embeddings = writeEmbedding
                        ? embeddingModel.embed(batch.stream().map(Document::getText).toList())
                        : null;

                // 构建 bulk 请求
                BulkRequest.Builder bulkBuilder = new BulkRequest.Builder().index(indexName);
                for (int i = 0; i < batch.size(); i++) {
                    Document doc = batch.get(i);

                    Map<String, Object> source = new LinkedHashMap<>();
                    source.put("content", doc.getText());
                    if (writeEmbedding) {
                        source.put("embedding", embeddings.get(i));
                    }
                    source.put("dataset_id", doc.getMetadata().get("dataset_id"));
                    source.put("dataset_file_id", doc.getMetadata().get("dataset_file_id"));
                    source.put("file_id", doc.getMetadata().get("file_id"));
                    source.put("source", doc.getMetadata().get("source"));
                    source.put("parent_id", doc.getMetadata().get("parent_id"));
                    source.put("parent_content", doc.getMetadata().get("parent_content"));
                    source.put("chunk_index", doc.getMetadata().get("chunk_index"));
                    // metadata 字段存去除大字段 parent_content 的副本，避免与 parent_content 重复存储
                    Map<String, Object> metaCopy = new LinkedHashMap<>(doc.getMetadata());
                    metaCopy.remove("parent_content");
                    source.put("metadata", metaCopy);

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

    // 混合检索(BM25 + dense + RRF 融合)已挪到 rag 的 HybridSearcher,统一走 DenseRetriever 策略,
    // 此处不再保留 ES-only 旁路,避免与 Milvus/ES 双路径不一致。ES 只暴露原子能力:
    // bm25SearchDocs(稀疏)/ knnSearchDocs(稠密),供 HybridSearcher 组合。

    private List<Hit> bm25Search(String indexName, String query, int size, String filterJson) throws Exception {
        String queryJson = (filterJson != null)
                ? "{\"bool\":{\"must\":[{\"match\":{\"content\":\"" + escapeJson(query) + "\"}}],\"filter\":" + filterJson + "}}"
                : "{\"match\":{\"content\":\"" + escapeJson(query) + "\"}}";
        String searchJson = "{\"query\":" + queryJson + ",\"size\":" + size + "}";

        try (InputStream is = new ByteArrayInputStream(searchJson.getBytes(StandardCharsets.UTF_8))) {
            SearchRequest req = SearchRequest.of(s -> s.index(indexName).withJson(is));
            SearchResponse<Map> resp = client.search(req, Map.class);
            return resp.hits().hits().stream()
                    .map(h -> new Hit(h.id(), toSource(h.source()), h.score() != null ? h.score() : 0))
                    .toList();
        }
    }

    private List<Hit> knnSearch(String indexName, float[] queryVector, int size, String filterJson) throws Exception {
        String knnClause = "{\"field\":\"embedding\",\"query_vector\":" + toJsonArray(queryVector)
                + ",\"k\":" + size + ",\"num_candidates\":" + (size * 10) + "}";
        if (filterJson != null) {
            knnClause = "{\"field\":\"embedding\",\"query_vector\":" + toJsonArray(queryVector)
                    + ",\"k\":" + size + ",\"num_candidates\":" + (size * 10) + ",\"filter\":" + filterJson + "}";
        }
        String searchJson = "{\"knn\":" + knnClause + ",\"size\":" + size + "}";

        try (InputStream is = new ByteArrayInputStream(searchJson.getBytes(StandardCharsets.UTF_8))) {
            SearchRequest req = SearchRequest.of(s -> s.index(indexName).withJson(is));
            SearchResponse<Map> resp = client.search(req, Map.class);
            return resp.hits().hits().stream()
                    .map(h -> new Hit(h.id(), toSource(h.source()), h.score() != null ? h.score() : 0))
                    .toList();
        }
    }

    // ==================== 公开检索(BM25 / KNN,供 rag 做 hybrid 融合) ====================

    /** BM25 检索,返回 Document(带 source 元数据),供 rag 取稀疏路召回 */
    public List<Document> bm25SearchDocs(String collectionName, String query, int topK) {
        String indexName = toIndexName(collectionName);
        try {
            return bm25Search(indexName, query, topK, null).stream().map(this::toDocument).toList();
        } catch (Exception e) {
            log.error("ES BM25 检索失败: index={}, query={}", indexName, query, e);
            return List.of();
        }
    }

    /** ES KNN 检索,返回 Document(score 为余弦相似度),供 EsKnnDenseRetriever 调用 */
    public List<Document> knnSearchDocs(String collectionName, String query, int topK) {
        String indexName = toIndexName(collectionName);
        try {
            float[] queryVector = embeddingModel.embed(query);
            return knnSearch(indexName, queryVector, topK, null).stream().map(this::toDocument).toList();
        } catch (Exception e) {
            log.error("ES KNN 检索失败: index={}, query={}", indexName, query, e);
            return List.of();
        }
    }

    /** Hit -> Document(携带 source 元数据) */
    private Document toDocument(Hit hit) {
        EsDocumentSource src = hit.source();
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", src.source());
        meta.put("dataset_id", src.datasetId());
        meta.put("dataset_file_id", src.datasetFileId());
        meta.put("file_id", src.fileId());
        meta.put("parent_id", src.parentId());
        meta.put("parent_content", src.parentContent());
        return Document.builder().text(src.content()).score(hit.score()).metadata(meta).build();
    }

    @SuppressWarnings("unchecked")
    private EsDocumentSource toSource(Object rawSource) {
        if (rawSource instanceof Map map) {
            return JacksonUtils.fromMap(map, EsDocumentSource.class);
        }
        return new EsDocumentSource("", "", null, null, null, null, null);
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
