package com.gj.llm.security.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 安全配置属性 —— 映射 {@code application.yaml} 中 {@code app.security} 前缀的配置。
 *
 * <p>包含 JWT 密钥、Token 有效期、白名单路径等可配置项，
 * 支持开发/生产环境差异化配置。</p>
 *
 * @author gj-llm
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /** JWT 相关配置 */
    private JwtConfig jwt = new JwtConfig();

    /**
     * 不需要认证即可访问的路径列表（Ant 风格路径匹配）。
     * 默认开放登录和刷新 Token 接口。
     */
    private List<String> ignorePaths = List.of(
            "/api/auth/login",
            "/api/auth/refresh"
    );

    @Getter
    @Setter
    public static class JwtConfig {

        /**
         * JWT 签名密钥（HMAC-SHA256），Base64 编码。
         * 生产环境必须配置（可用环境变量注入，如 {@code ${JWT_SECRET}}）；
         * 多实例部署所有实例必须使用同一密钥，否则跨实例验签失败。
         * 留空时自动生成临时密钥，服务重启后所有 Token 失效。
         */
        private String secret = "";

        /** Access Token 有效期，单位毫秒，默认 24 小时 */
        private long accessTokenExpiration = 24 * 60 * 60 * 1000L;

        /** Refresh Token 有效期，单位毫秒，默认 7 天 */
        private long refreshTokenExpiration = 7 * 24 * 60 * 60 * 1000L;
    }
}
