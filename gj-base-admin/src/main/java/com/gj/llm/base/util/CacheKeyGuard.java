package com.gj.llm.base.util;

import com.gj.llm.common.exception.WarnBusinessException;

import java.util.regex.Pattern;

/**
 * 缓存键入参守卫 -- 校验客户端传入的 dictType / configKey 等作为 Redis key 组成部分的参数格式。
 *
 * <p>这类值会被直接拼进缓存 key（如 {@code sys:config:key:{key}}），不校验格式时
 * 登录用户可用随机串批量制造无主缓存键（Redis 内存膨胀 + 每次 miss 打库）。</p>
 *
 * @author gj-llm
 */
public final class CacheKeyGuard {

    /** 合法 key 片段：字母/数字/点/下划线/中划线，1-100 位 */
    private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    private CacheKeyGuard() {
    }

    /**
     * 校验 key 片段格式，不合法直接拒绝。
     *
     * @param key  客户端传入的 key 片段（dictType / configKey 等）
     * @param name 参数名（用于错误提示，如 "字典类型"、"参数键名"）
     */
    public static void check(String key, String name) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new WarnBusinessException("cache.keyInvalid", name);
        }
    }
}
