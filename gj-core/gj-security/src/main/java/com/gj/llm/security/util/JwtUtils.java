package com.gj.llm.security.util;

import com.gj.llm.security.properties.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecureDigestAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌工具类 —— 负责 Access Token 和 Refresh Token 的签发、校验与解析。
 *
 * <p>使用 HMAC-SHA256 签名算法，密钥可通过配置指定或由系统自动生成。
 * Token Payload 中携带 {@code userId}、{@code username} 和 {@code type} 等声明，
 * 供过滤器与业务层使用。</p>
 *
 * <h3>Token 类型说明</h3>
 * <ul>
 *   <li><b>Access Token</b>：短期令牌（默认 24h），用于日常 API 请求认证</li>
 *   <li><b>Refresh Token</b>：长期令牌（默认 7d），仅用于换取新的 Access Token</li>
 * </ul>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    // ==================== 常量 ====================

    /** Token 类型：短期访问令牌 */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /** Token 类型：长期刷新令牌 */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /** JWT Claims 键名：用户 ID */
    private static final String CLAIM_USER_ID = "userId";

    /** JWT Claims 键名：用户名 */
    private static final String CLAIM_USERNAME = "username";

    /** JWT Claims 键名：Token 类型（access / refresh） */
    private static final String CLAIM_TOKEN_TYPE = "type";

    /** 自动生成密钥时的安全随机数生成器 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** HMAC-SHA256 签名算法 */
    private static final SecureDigestAlgorithm<SecretKey, ?> ALGORITHM = Jwts.SIG.HS256;

    // ==================== 注入依赖 ====================

    private final SecurityProperties securityProperties;

    /** HMAC 签名密钥，在 init() 中初始化 */
    private SecretKey secretKey;

    // ==================== 初始化 ====================

    /**
     * Bean 初始化后执行：加载或自动生成 JWT 签名密钥。
     *
     * <p>若配置了 {@code app.security.jwt.secret} 则使用配置值（Base64 解码）；
     * 否则使用 {@link SecureRandom} 生成 256-bit 随机密钥。
     * <b>注意：</b>自动生成的密钥在服务重启后失效，所有已签发 Token 将不可校验。</p>
     */
    @PostConstruct
    public void init() {
        String configuredSecret = securityProperties.getJwt().getSecret();
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            byte[] keyBytes = Decoders.BASE64.decode(configuredSecret);
            this.secretKey = Keys.hmacShaKeyFor(keyBytes);
            log.info("JWT 签名密钥已从配置加载");
        } else {
            byte[] keyBytes = new byte[32]; // 256 bits
            SECURE_RANDOM.nextBytes(keyBytes);
            this.secretKey = Keys.hmacShaKeyFor(keyBytes);
            String generatedSecret = Base64.getEncoder().encodeToString(keyBytes);
            log.warn("未配置 app.security.jwt.secret，已自动生成随机密钥。" +
                     "服务重启后所有 Token 将失效。生成值: {}", generatedSecret);
        }
    }

    // ==================== Token 签发 ====================

    /**
     * 签发 Access Token（短期访问令牌）。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return JWT 字符串（不含 Bearer 前缀）
     */
    public String generateAccessToken(Long userId, String username) {
        long expiration = securityProperties.getJwt().getAccessTokenExpiration();
        return buildToken(userId, username, TOKEN_TYPE_ACCESS, expiration);
    }

    /**
     * 签发 Refresh Token（长期刷新令牌）。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return JWT 字符串
     */
    public String generateRefreshToken(Long userId, String username) {
        long expiration = securityProperties.getJwt().getRefreshTokenExpiration();
        return buildToken(userId, username, TOKEN_TYPE_REFRESH, expiration);
    }

    // ==================== Token 校验 ====================

    /**
     * 校验 Access Token 是否有效（签名 + 过期 + type 必须为 "access"）。
     *
     * @param token JWT 令牌字符串
     * @return true=有效，false=无效
     */
    public boolean validateAccessToken(String token) {
        return validateToken(token, TOKEN_TYPE_ACCESS);
    }

    /**
     * 校验 Refresh Token 是否有效（签名 + 过期 + type 必须为 "refresh"）。
     *
     * @param token JWT 令牌字符串
     * @return true=有效，false=无效
     */
    public boolean validateRefreshToken(String token) {
        return validateToken(token, TOKEN_TYPE_REFRESH);
    }

    // ==================== Token 解析 ====================

    /**
     * 从 Token 中提取用户 ID。
     *
     * @param token JWT 令牌字符串
     * @return 用户 ID
     * @throws JwtException 若 Token 无效
     */
    public Long getUserId(String token) {
        return parseClaims(token).get(CLAIM_USER_ID, Long.class);
    }

    /**
     * 从 Token 中提取用户名。
     *
     * @param token JWT 令牌字符串
     * @return 用户名
     * @throws JwtException 若 Token 无效
     */
    public String getUsername(String token) {
        return parseClaims(token).get(CLAIM_USERNAME, String.class);
    }

    // ==================== 内部方法 ====================

    /**
     * 构建 JWT Token。
     */
    private String buildToken(Long userId, String username, String tokenType, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())       // jti: 唯一标识，防止重放攻击
                .subject(String.valueOf(userId))         // sub: 用户 ID
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TOKEN_TYPE, tokenType)      // 区分 access / refresh
                .issuedAt(now)                            // iat: 签发时间
                .expiration(expiry)                       // exp: 过期时间
                .signWith(secretKey, ALGORITHM)           // HMAC-SHA256 签名
                .compact();
    }

    /**
     * 校验 JWT Token（签名 + 类型匹配）。
     */
    private boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            String actualType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            return expectedType.equals(actualType);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析 JWT Token 为 Claims 对象。
     *
     * @param token JWT 字符串
     * @return Claims 载荷
     * @throws JwtException 签名无效、Token 过期、格式错误等
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
