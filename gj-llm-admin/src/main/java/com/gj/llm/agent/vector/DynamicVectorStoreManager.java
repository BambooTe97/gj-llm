package com.gj.llm.agent.vector;

import com.gj.llm.agent.constant.VectorStoreConstants;
import io.milvus.client.MilvusServiceClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DynamicVectorStoreManager {

    private final Map<String, VectorStore> storeCache = new ConcurrentHashMap<>();

    private final EmbeddingModel embeddingModel;
    private final MilvusServiceClient milvusClient;

    public DynamicVectorStoreManager(EmbeddingModel embeddingModel, MilvusServiceClient milvusClient) {
        this.embeddingModel = embeddingModel;
        this.milvusClient = milvusClient;
    }

    /**
     * 获取指定类型的 VectorStore（按 collectionName 区分）。
     * <p>集合命名规范：{@code collection_} + type，例如 type 为 "medical" 则集合名为 "collection_medical"。</p>
     *
     * @param type 类型标识（如 "medical"、"story"，不含前缀）
     * @return VectorStore 实例
     */
    public VectorStore getVectorStore(String type) {
        return storeCache.computeIfAbsent(type, this::createNewVectorStore);
    }

    private VectorStore createNewVectorStore(String type) {
        String collectionName = VectorStoreConstants.COLLECTION_PREFIX + type;
        return MilvusVectorStore.builder(milvusClient, embeddingModel)
                .collectionName(collectionName)
                .databaseName(VectorStoreConstants.DEFAULT_DATABASE)
                .initializeSchema(true)
                .build();
    }
}
