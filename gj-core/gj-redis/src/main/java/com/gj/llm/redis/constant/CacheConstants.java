package com.gj.llm.redis.constant;

/**
 * Redis 缓存常量 -- 统一管理缓存 key 前缀与默认 TTL，避免散落在各业务模块中。
 *
 * <h3>命名约定</h3>
 * <ul>
 *   <li>key 前缀以模块/用途分组，以 {@code :} 分层，如 {@code login:user:}、{@code auth:blacklist:}</li>
 * </ul>
 *
 * @author gj-llm
 */
public final class CacheConstants {

    private CacheConstants() {
    }

    // ==================== 登录用户缓存 ====================

    /** 安全用户缓存 key 前缀，完整 key = {@code login:user:{username}} */
    public static final String LOGIN_USER_KEY = "login:user:";

    /** 安全用户缓存默认有效期（30 分钟） */
    public static final long LOGIN_USER_TTL_MINUTES = 30;

    // ==================== Token 黑名单 ====================

    /** 登出 Token 黑名单 key 前缀，完整 key = {@code auth:blacklist:{token}} */
    public static final String TOKEN_BLACKLIST_KEY = "auth:blacklist:";

}
