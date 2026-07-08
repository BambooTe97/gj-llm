package com.gj.llm.base.controller;

import com.gj.llm.base.model.LoginRequest;
import com.gj.llm.base.model.LoginResponse;
import com.gj.llm.base.service.AuthService;
import com.gj.llm.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器 —— 提供登录、登出、Token 刷新接口。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>POST /api/auth/login   — 登录</li>
 *   <li>POST /api/auth/logout  — 登出（需 Bearer Token）</li>
 *   <li>POST /api/auth/refresh — 刷新 Access Token（需 Refresh Token）</li>
 * </ul>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** HTTP Authorization 头的 Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 用户登录。
     *
     * @param request {username, password}
     * @return {accessToken, refreshToken, username, nickname, avatar}
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.ok(response, "登录成功");
    }

    /**
     * 刷新 Access Token。
     *
     * <p>请求头需携带 Refresh Token（而非 Access Token）。
     * 返回新的 Access Token，Refresh Token 不变。</p>
     *
     * @param authHeader Authorization 头（Bearer <refreshToken>）
     * @return 新的 accessToken
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        // 提取 Refresh Token
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return ApiResponse.unauthorized("缺少 Refresh Token");
        }
        String refreshToken = authHeader.substring(BEARER_PREFIX.length());

        String newAccessToken = authService.refreshAccessToken(refreshToken);
        LoginResponse response = LoginResponse.builder()
                .accessToken(newAccessToken)
                .build();
        return ApiResponse.ok(response, "Token 刷新成功");
    }

    /**
     * 用户登出。
     *
     * <p>当前为无状态 JWT 模式，服务端仅记录日志，客户端需自行清除 Token。</p>
     *
     * @param authHeader Authorization 头
     * @return 成功响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            authService.logout(token);
        }
        return ApiResponse.ok(null, "登出成功");
    }
}
