package com.gj.llm.agent.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    @Value("${spring.ai.vectorstore.milvus.client.host}")
    private String host;

    @Value("${spring.ai.vectorstore.milvus.client.port}")
    private int port;

    @Value("${spring.ai.vectorstore.milvus.client.username}")
    private String username;

    @Value("${spring.ai.vectorstore.milvus.client.password}")
    private String password;

    @Value("${spring.ai.vectorstore.milvus.database-name:default}")
    private String databaseName;

    @Bean
    public MilvusClientV2 milvusClientV2() {
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://" + host + ":" + port)
                .username(username)
                .password(password)
                .dbName(databaseName)
                .build();
        return new MilvusClientV2(config);
    }
}
