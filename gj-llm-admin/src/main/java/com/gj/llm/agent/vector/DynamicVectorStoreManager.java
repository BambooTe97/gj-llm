package com.gj.llm.agent.vector;

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
     * 获取特定类型的 VectorStore（按 collectionName 区分）
     * @param type 类型 (如 "medical", "story")
     * @return VectorStore 实例
     */
    public VectorStore getVectorStore(String type) {
        return storeCache.computeIfAbsent(type, this::createNewVectorStore);
    }

    private VectorStore createNewVectorStore(String type) {
        String collectionName = "collection_" + type;
        return MilvusVectorStore.builder(milvusClient, embeddingModel)
                .collectionName(collectionName)
                .databaseName("default")
                .initializeSchema(true)
                .build();
    }
}
