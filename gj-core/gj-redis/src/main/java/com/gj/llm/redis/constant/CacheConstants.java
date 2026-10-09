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

    // ==================== 登录防暴力破解 ====================

    /** 登录失败计数 key 前缀，完整 key = {@code login:fail:{username}}，value = 失败次数 */
    public static final String LOGIN_FAIL_KEY = "login:fail:";

    /** 账号锁定 key 前缀，完整 key = {@code login:lock:{username}}，存在即锁定 */
    public static final String LOGIN_LOCK_KEY = "login:lock:";

    // ==================== 在线会话注册表 ====================

    /** 在线会话注册表 key 前缀，完整 key = {@code online:token:{refreshTokenJti}} */
    public static final String ONLINE_USER_KEY = "online:token:";

    // ==================== 字典/参数读缓存 ====================

    /** 字典数据读缓存 key 前缀，完整 key = {@code sys:dict:data:{dictType}}，value = 启用态字典数据列表 */
    public static final String SYS_DICT_DATA_KEY = "sys:dict:data:";

    /** 参数读缓存 key 前缀，完整 key = {@code sys:config:key:{configKey}}，value = 参数实体 */
    public static final String SYS_CONFIG_KEY = "sys:config:key:";

    /** 字典/参数读缓存默认有效期（30 分钟，变更事件失效的兜底 TTL） */
    public static final long SYS_CACHE_TTL_MINUTES = 30;

    // ==================== 滑动验证码 ====================

    /** 滑动验证码 key 前缀，完整 key = {@code captcha:slide:{captchaToken}}，value = 缺口 x 坐标 */
    public static final String CAPTCHA_SLIDE_KEY = "captcha:slide:";

    /** 滑动验证码失败尝试计数 key 前缀，完整 key = {@code captcha:fail:{captchaToken}} */
    public static final String CAPTCHA_FAIL_KEY = "captcha:fail:";

    /** 滑动验证码生成限流 key 前缀，完整 key = {@code captcha:gen:{ip}}，value = 次数 */
    public static final String CAPTCHA_GEN_KEY = "captcha:gen:";

}
