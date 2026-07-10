package com.gj.llm.rag.constant;

/**
 * 向量库相关常量。
 *
 * @author gj-llm
 */
public final class VectorStoreConstants {

    private VectorStoreConstants() {
    }

    /** Milvus 集合名前缀，完整集合名 = {@value} + type */
    public static final String COLLECTION_PREFIX = "collection_";

    /** 默认数据库名 */
    public static final String DEFAULT_DATABASE = "default";
}
