package com.gj.llm.base.service.impl;

import com.gj.llm.redis.constant.CacheConstants;
import com.gj.llm.redis.service.RedisService;
import com.gj.llm.security.service.TokenBlacklistService;
import com.gj.llm.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的 Token 黑名单实现 -- 登出时将 Access Token 写入 Redis，
 * TTL 取其剩余有效期；认证过滤器通过 {@link #isBlacklisted} 校验，实现登出即时失效。
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private final RedisService redisService;
    private final JwtUtils jwtUtils;

    @Override
    public void blacklist(String token) {
        Duration ttl = jwtUtils.getRemainingDuration(token);
        if (ttl.isZero() || ttl.isNegative()) {
            // Token 已过期或无效，无需加入黑名单
            return;
        }
        redisService.set(CacheConstants.TOKEN_BLACKLIST_KEY + token, "1", ttl);
        log.info("Token 已加入黑名单，TTL={}s", ttl.getSeconds());
    }

    @Override
    public boolean isBlacklisted(String token) {
        return redisService.hasKey(CacheConstants.TOKEN_BLACKLIST_KEY + token);
    }
}
