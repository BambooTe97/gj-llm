package com.gj.llm.rag.vector;

import com.gj.llm.rag.constant.VectorStoreConstants;
import io.milvus.client.MilvusServiceClient;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DynamicVectorStoreManager {

    private final Map<String, VectorStore> storeCache = new ConcurrentHashMap<>();

    private final EmbeddingModel embeddingModel;
    private final MilvusClientV2 milvusClientV2;
    private final MilvusServiceClient milvusClient;

    public DynamicVectorStoreManager(EmbeddingModel embeddingModel,
                                     MilvusClientV2 milvusClientV2,
                                     MilvusServiceClient milvusClient) {
        this.embeddingModel = embeddingModel;
        this.milvusClientV2 = milvusClientV2;
        this.milvusClient = milvusClient;
    }

    public VectorStore getVectorStore(String type) {
        return storeCache.computeIfAbsent(type, t -> {
            ensureCollectionExists(t);
            return buildVectorStore(t);
        });
    }

    private void ensureCollectionExists(String type) {
        String collectionName = VectorStoreConstants.COLLECTION_PREFIX + type;

        Boolean exists = milvusClientV2.hasCollection(
                HasCollectionReq.builder().collectionName(collectionName).build());

        if (Boolean.TRUE.equals(exists)) {
            log.info("Milvus 集合已存在: {}", collectionName);
            return;
        }

        // 使用 MilvusClientV2 创建 schema，字段与 Spring AI MilvusVectorStore 兼容
        CreateCollectionReq.CollectionSchema schema = milvusClientV2.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName("doc_id")
                .dataType(DataType.VarChar)
                .maxLength(36)
                .isPrimaryKey(true)
                .autoID(true)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("content")
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("metadata")
                .dataType(DataType.JSON)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding")
                .dataType(DataType.FloatVector)
                .dimension(1024)
                .build());

        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .databaseName(VectorStoreConstants.DEFAULT_DATABASE)
                .collectionName(collectionName)
                .description("Spring AI Vector Store")
                .collectionSchema(schema)
                .build();

        milvusClientV2.createCollection(createReq);
        log.info("Milvus 集合创建成功: {}", collectionName);

        // 创建 embedding 字段索引（HNSW：高召回率，适合小数据量到中等数据量）
        IndexParam indexParam = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of(
                        "M", "16",
                        "efConstruction", "200"))
                .build();

        milvusClientV2.createIndex(CreateIndexReq.builder()
                .databaseName(VectorStoreConstants.DEFAULT_DATABASE)
                .collectionName(collectionName)
                .indexParams(List.of(indexParam))
                .build());
        log.info("Milvus 索引创建成功: {}, field=embedding", collectionName);

        // 加载集合到内存
        milvusClientV2.loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName)
                .build());
        log.info("Milvus 集合加载成功: {}", collectionName);
    }

    private VectorStore buildVectorStore(String type) {
        String collectionName = VectorStoreConstants.COLLECTION_PREFIX + type;
        return MilvusVectorStore.builder(milvusClient, embeddingModel)
                .collectionName(collectionName)
                .databaseName(VectorStoreConstants.DEFAULT_DATABASE)
                .initializeSchema(false)
                .autoId(true)
                .build();
    }

    /** 删除 Milvus 集合并清空缓存 */
    public void dropCollection(String type) {
        String collectionName = VectorStoreConstants.COLLECTION_PREFIX + type;
        try {
            milvusClientV2.dropCollection(
                    DropCollectionReq.builder().collectionName(collectionName).build());
            storeCache.remove(type);
            log.info("Milvus 集合删除成功: {}", collectionName);
        } catch (Exception e) {
            log.warn("Milvus 集合删除失败（可能不存在）: {}", collectionName);
        }
    }
}
