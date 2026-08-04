package com.gj.llm.security.service;

/**
 * Token 黑名单服务抽象 -- 支持登出后将 Access Token 加入黑名单，并在认证时校验。
 *
 * <p>由具备持久化能力（如 Redis）的模块提供实现。未提供实现时，{@code JwtAuthenticationFilter}
 * 退化为不校验黑名单（保持无状态 JWT 行为），保证安全模块可独立使用。</p>
 *
 * @author gj-llm
 */
public interface TokenBlacklistService {

    /**
     * 将 Token 加入黑名单。
     *
     * <p>实现应以 Token 的剩余有效期为 TTL，使黑名单条目随 Token 自然过期清除。</p>
     *
     * @param token JWT 令牌字符串
     */
    void blacklist(String token);

    /**
     * 判断 Token 是否已被加入黑名单。
     *
     * @param token JWT 令牌字符串
     * @return true=已黑名单（应拒绝），false=未黑名单
     */
    boolean isBlacklisted(String token);
}
