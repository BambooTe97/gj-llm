package com.gj.llm.base.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 在线会话记录 -- Redis 注册表条目（{@code online:token:{refreshTokenJti}}）。
 *
 * <p>每次登录产生一个会话条目，TTL = Refresh Token 剩余有效期；
 * 刷新 Access Token 时原地更新 accessToken 字段，key（tokenId）保持稳定。
 * accessToken/refreshToken 字段供强制下线时加入黑名单使用，仅存于服务端 Redis。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineUserRecord {

    /** 会话标识（= Refresh Token 的 jti） */
    private String tokenId;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 登录 IP */
    private String ip;

    /** 浏览器（UA 解析） */
    private String browser;

    /** 操作系统（UA 解析） */
    private String os;

    /** 登录时间 */
    private LocalDateTime loginTime;

    /** 当前 Access Token（刷新后更新，强退时入黑名单） */
    private String accessToken;

    /** Refresh Token（会话级，强退时入黑名单） */
    private String refreshToken;
}
