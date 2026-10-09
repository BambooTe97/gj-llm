package com.gj.llm.base.controller;

import com.gj.llm.base.annotation.OperLog;
import com.gj.llm.base.model.ChangePasswordRequest;
import com.gj.llm.base.model.LoginRequest;
import com.gj.llm.base.model.LoginResponse;
import com.gj.llm.base.model.UserInfoResponse;
import com.gj.llm.base.service.AuthService;
import com.gj.llm.common.util.SecurityUtils;
import com.gj.llm.common.web.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器 —— 提供登录、登出、Token 刷新、自助修改密码接口。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>POST /api/auth/login   — 登录</li>
 *   <li>POST /api/auth/logout  — 登出（需 Bearer Token）</li>
 *   <li>POST /api/auth/refresh — 刷新 Access Token（需 Refresh Token）</li>
 *   <li>POST /api/auth/change-password — 自助修改密码（登录即可，本人自服务）</li>
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
     * @param httpRequest 当前请求（采集登录 IP / User-Agent，用于在线会话）
     * @return {accessToken, refreshToken, username, nickname, avatar}
     */
    @OperLog(module = "认证管理", type = "登录")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, httpRequest);
        return R.ok(response, "登录成功");
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
    @OperLog(module = "认证管理", type = "刷新Token")
    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        // 提取 Refresh Token
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return R.unauthorized("缺少 Refresh Token");
        }
        String refreshToken = authHeader.substring(BEARER_PREFIX.length());

        String newAccessToken = authService.refreshAccessToken(refreshToken);
        LoginResponse response = LoginResponse.builder()
                .accessToken(newAccessToken)
                .build();
        return R.ok(response, "Token 刷新成功");
    }

    /**
     * 用户登出。
     *
     * <p>Access Token 加入黑名单并移除在线会话条目，客户端需自行清除 Token。</p>
     *
     * @param authHeader Authorization 头
     * @return 成功响应
     */
    @OperLog(module = "认证管理", type = "登出")
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            authService.logout(token);
        }
        return R.ok(null, "登出成功");
    }

    /**
     * 获取当前登录用户信息（含角色、权限标识、菜单树）。
     *
     * <p>需携带 Access Token。前端登录后及页面刷新时调用，
     * 据此动态注册路由、渲染导航、做按钮级权限控制。</p>
     *
     * @return 用户信息响应
     */
    @GetMapping("/userinfo")
    public R<UserInfoResponse> userinfo() {
        return R.ok(authService.getCurrentUserInfo());
    }

    /**
     * 自助修改密码。
     *
     * <p>登录即可调用（无需权限点，与 userinfo/logout 同语义）。
     * 校验原密码与新密码复杂度策略，改密后当前 Token 保留至自然过期。</p>
     *
     * @param request {oldPassword, newPassword}
     * @return 成功响应
     */
    @OperLog(module = "认证管理", type = "修改密码")
    @PostMapping("/change-password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.getCurrentUsername(), request);
        return R.ok(null, "密码修改成功");
    }
}
