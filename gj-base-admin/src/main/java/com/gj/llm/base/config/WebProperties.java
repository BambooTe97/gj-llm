package com.gj.llm.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Web 请求上下文配置。
 *
 * <p>配置前缀 {@code gj.llm.web}。</p>
 *
 * @author gj-llm
 */
@Data
@Component
@ConfigurationProperties(prefix = "gj.llm.web")
public class WebProperties {

    /**
     * 是否信任 X-Forwarded-For / X-Real-IP 头解析客户端 IP。
     *
     * <p>仅当部署在可信反向代理（Nginx 等）之后时开启；直接对外暴露时客户端可伪造该头，
     * 污染操作日志/登录日志的 IP 审计与验证码限流依据。</p>
     */
    private boolean trustXff = false;
}
