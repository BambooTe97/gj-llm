package com.gj.llm.rag.config;

import com.gj.llm.common.constant.Constants;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientProperties;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({MilvusServiceClientProperties.class, MilvusVectorStoreProperties.class})
public class MilvusConfig {

    @Bean
    public MilvusClientV2 milvusClientV2(MilvusServiceClientProperties properties, MilvusVectorStoreProperties milvusVectorStoreProperties) {
        ConnectConfig config = ConnectConfig.builder()
                .uri(Constants.HTTP + properties.getHost() + ":" + properties.getPort())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .dbName(milvusVectorStoreProperties.getDatabaseName())
                .build();
        return new MilvusClientV2(config);
    }
}
