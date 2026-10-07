package com.gj.llm.base.service;

import com.gj.llm.base.model.OnlineUserRecord;
import com.gj.llm.base.model.OnlineUserVO;
import com.gj.llm.base.service.impl.SecurityUserServiceImpl;
import com.gj.llm.redis.constant.CacheConstants;
import com.gj.llm.redis.service.RedisService;
import com.gj.llm.security.service.TokenBlacklistService;
import com.gj.llm.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 在线用户服务 -- 基于 Redis 的在线会话注册表（非数据库实体，故不走 IService）。
 *
 * <h3>设计</h3>
 * <ul>
 *   <li>注册表：{@code online:token:{refreshTokenJti}} → {@link OnlineUserRecord}，
 *       每次登录一个条目，TTL = Refresh Token 剩余有效期</li>
 *   <li>刷新 Access Token 时原地更新条目的 accessToken 字段，key 保持稳定</li>
 *   <li>强制下线：将 accessToken + refreshToken 一并加入黑名单
 *       （仅拉黑 access 会被 7 天有效的 refresh token 继续换新），再删除条目</li>
 *   <li>列表返回 {@link OnlineUserVO}，不外泄 Token</li>
 * </ul>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserService {

    private final RedisService redisService;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final SecurityUserServiceImpl securityUserService;

    /**
     * 注册会话（登录成功时调用）。
     *
     * @param record 会话记录（含双 Token）
     */
    public void register(OnlineUserRecord record) {
        Duration ttl = jwtUtils.getRemainingDuration(record.getRefreshToken());
        if (!ttl.isZero()) {
            redisService.set(CacheConstants.ONLINE_USER_KEY + record.getTokenId(), record, ttl);
        }
    }

    /**
     * 更新会话的 Access Token（刷新时调用；条目不存在则忽略）。
     *
     * @param refreshToken 当前 Refresh Token（定位条目）
     * @param newAccessToken 新签发的 Access Token
     */
    public void updateAccessToken(String refreshToken, String newAccessToken) {
        String key = CacheConstants.ONLINE_USER_KEY + jwtUtils.getJti(refreshToken);
        OnlineUserRecord record = redisService.get(key, OnlineUserRecord.class);
        if (record != null) {
            record.setAccessToken(newAccessToken);
            redisService.set(key, record, jwtUtils.getRemainingDuration(refreshToken));
        }
    }

    /**
     * 登出时移除会话条目。
     *
     * <p>注册表按 Refresh Token 的 jti 键控，登出只拿得到 Access Token，
     * 故扫描匹配 accessToken 后删除（不能按 username 匹配——多端会话）。</p>
     *
     * @param accessToken 当前登出的 Access Token
     */
    public void removeByAccessToken(String accessToken) {
        List<String> keys = redisService.scanKeys(CacheConstants.ONLINE_USER_KEY + "*");
        for (String key : keys) {
            OnlineUserRecord record = redisService.get(key, OnlineUserRecord.class);
            if (record != null && Objects.equals(accessToken, record.getAccessToken())) {
                redisService.delete(key);
            }
        }
    }

    /**
     * 在线会话列表，按登录时间倒序。
     *
     * @return 在线用户视图（不含 Token）
     */
    public List<OnlineUserVO> list() {
        List<String> keys = redisService.scanKeys(CacheConstants.ONLINE_USER_KEY + "*");
        return keys.stream()
                .map(key -> redisService.get(key, OnlineUserRecord.class))
                .filter(Objects::nonNull)
                // SCAN 期间过期的条目 get 返回 null，需过滤
                .filter(record -> !jwtUtils.getRemainingDuration(record.getRefreshToken()).isZero()
                        && !tokenBlacklistService.isBlacklisted(record.getAccessToken()))
                .sorted(Comparator.comparing(OnlineUserRecord::getLoginTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(record -> OnlineUserVO.builder()
                        .tokenId(record.getTokenId())
                        .userId(record.getUserId())
                        .username(record.getUsername())
                        .nickname(record.getNickname())
                        .ip(record.getIp())
                        .browser(record.getBrowser())
                        .os(record.getOs())
                        .loginTime(record.getLoginTime())
                        .build())
                .toList();
    }

    /**
     * 强制下线：双 Token 入黑名单 → 删除会话条目 → 失效用户权限缓存。
     *
     * @param tokenId 会话标识（Refresh Token 的 jti）
     */
    public void forceLogout(String tokenId) {
        String key = CacheConstants.ONLINE_USER_KEY + tokenId;
        OnlineUserRecord record = redisService.get(key, OnlineUserRecord.class);
        if (record == null) {
            throw new RuntimeException("会话不存在或已下线");
        }
        // 双 Token 均入黑名单：只拉黑 access 会被 refresh 继续换新
        blacklistQuietly(record.getAccessToken());
        blacklistQuietly(record.getRefreshToken());
        redisService.delete(key);
        // 失效 30 分钟的用户权限缓存，被禁用/被改角色的用户立即生效
        securityUserService.evict(record.getUsername());
        log.info("强制下线: username={}, tokenId={}", record.getUsername(), tokenId);
    }

    private void blacklistQuietly(String token) {
        try {
            tokenBlacklistService.blacklist(token);
        } catch (Exception e) {
            log.warn("[在线用户] Token 入黑名单失败（忽略）: {}", e.getMessage());
        }
    }
}
