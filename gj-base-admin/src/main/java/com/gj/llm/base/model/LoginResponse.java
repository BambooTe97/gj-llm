package com.gj.llm.base.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 DTO —— 对应前端 {@code LoginResponse} 类型。
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT Access Token（短期，默认 24h 有效） */
    private String accessToken;

    /** JWT Refresh Token（长期，默认 7d 有效），用于刷新 Access Token */
    private String refreshToken;

    /** 用户名 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;
}
