package com.gj.llm.base.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线用户视图对象 -- 在线用户列表返回。
 *
 * <p>不包含 accessToken/refreshToken（Token 仅存于服务端 Redis，不得外泄）。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
public class OnlineUserVO {

    /** 会话标识（强退接口入参） */
    private String tokenId;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 登录 IP */
    private String ip;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 登录时间 */
    private LocalDateTime loginTime;
}
