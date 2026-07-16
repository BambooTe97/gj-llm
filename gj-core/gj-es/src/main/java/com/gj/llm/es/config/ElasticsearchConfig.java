package com.gj.llm.es.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    private final EsProperties esProperties;

    public ElasticsearchConfig(EsProperties esProperties) {
        this.esProperties = esProperties;
    }

    @Bean(destroyMethod = "close")
    public RestClientTransport restClientTransport() {
        RestClient restClient = RestClient.builder(
                HttpHost.create("http://" + esProperties.getHost() + ":" + esProperties.getPort())
        ).build();
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClientTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
