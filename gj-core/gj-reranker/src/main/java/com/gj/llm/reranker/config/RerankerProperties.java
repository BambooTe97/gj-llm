package com.gj.llm.reranker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gj.llm.reranker")
public class RerankerProperties {

    /** 是否启用 reranker 精排 */
    private boolean enabled = true;

    /** reranker 服务地址 */
    private String host = "localhost";

    /** reranker 服务端口 */
    private int port = 80;

    /** HTTP 请求超时（毫秒） */
    private int timeout = 30_000;
}
