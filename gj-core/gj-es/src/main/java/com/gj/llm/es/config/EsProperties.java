package com.gj.llm.es.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gj.llm.es")
public class EsProperties {

    /** ES 地址 */
    private String host = "localhost";

    /** ES 端口 */
    private int port = 9200;

    /** 索引名前缀 */
    private String indexPrefix = "rag_";

    /** embedding 维度 */
    private int embeddingDimension = 1024;

    /** 索引分片数（单节点设为 1） */
    private int shards = 1;

    /** 副本数（单节点设为 0） */
    private int replicas = 0;
}
