package com.gj.llm.base.service;

import com.gj.llm.base.model.LoginRequest;
import com.gj.llm.base.model.LoginResponse;

/**
 * 认证服务接口 —— 处理用户登录、登出、Token 刷新。
 *
 * @author gj-llm
 */
public interface AuthService {

    /**
     * 用户登录：校验用户名/密码，签发 JWT。
     *
     * @param request 登录请求
     * @return 包含 Access Token 和 Refresh Token 的响应
     */
    LoginResponse login(LoginRequest request);

    /**
     * 刷新 Access Token：用有效的 Refresh Token 换取新的 Access Token。
     *
     * @param refreshToken 请求头中的 Refresh Token（不含 Bearer 前缀）
     * @return 新的 Access Token
     */
    String refreshAccessToken(String refreshToken);

    /**
     * 用户登出。
     *
     * <p>当前为无状态 JWT 模式，服务端无需额外操作（客户端自行清除 Token）。
     * 若后续引入 Token 黑名单机制，可在此实现。</p>
     *
     * @param accessToken 请求头中的 Access Token
     */
    void logout(String accessToken);
}
