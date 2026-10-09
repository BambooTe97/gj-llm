package com.gj.llm.base.service;

import com.gj.llm.base.model.ChangePasswordRequest;
import com.gj.llm.base.model.LoginRequest;
import com.gj.llm.base.model.LoginResponse;
import com.gj.llm.base.model.UserInfoResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 认证服务接口 —— 处理用户登录、登出、Token 刷新。
 *
 * @author gj-llm
 */
public interface AuthService {

    /**
     * 用户登录：校验用户名/密码（含失败锁定检查），签发 JWT 并注册在线会话。
     *
     * @param request 登录请求
     * @param httpRequest 当前请求（用于采集登录 IP / User-Agent）
     * @return 包含 Access Token 和 Refresh Token 的响应
     */
    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest);

    /**
     * 刷新 Access Token：用有效的 Refresh Token 换取新的 Access Token。
     *
     * @param refreshToken 请求头中的 Refresh Token（不含 Bearer 前缀）
     * @return 新的 Access Token
     */
    String refreshAccessToken(String refreshToken);

    /**
     * 用户登出：Access Token 入黑名单 + 移除在线会话条目。
     *
     * @param accessToken 请求头中的 Access Token
     */
    void logout(String accessToken);

    /**
     * 获取当前登录用户信息（含角色、权限标识、菜单树）。
     *
     * <p>供前端登录后及页面刷新时调用，据此动态注册路由、渲染导航、做按钮级权限控制。</p>
     *
     * @return 用户信息响应
     */
    UserInfoResponse getCurrentUserInfo();

    /**
     * 自助修改密码：校验原密码 + 新密码复杂度策略，通过后更新并失效安全用户缓存。
     *
     * <p>本人自服务接口（登录即可调用，无需权限点）。当前已签发的 Access Token
     * 不强制下线，保留至自然过期。</p>
     *
     * @param username 当前登录用户名
     * @param request  原密码 + 新密码
     */
    void changePassword(String username, ChangePasswordRequest request);
}
