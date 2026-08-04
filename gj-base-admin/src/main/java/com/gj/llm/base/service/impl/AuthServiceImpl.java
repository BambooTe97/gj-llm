package com.gj.llm.base.service.impl;

import com.gj.llm.base.entity.MenuEntity;
import com.gj.llm.base.model.LoginRequest;
import com.gj.llm.base.model.LoginResponse;
import com.gj.llm.base.model.UserInfoResponse;
import com.gj.llm.base.service.AuthService;
import com.gj.llm.base.service.MenuService;
import com.gj.llm.security.model.SecurityUser;
import com.gj.llm.security.service.TokenBlacklistService;
import com.gj.llm.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务实现 —— 处理登录、Token 刷新、登出等核心认证逻辑。
 *
 * <h3>登录流程</h3>
 * <ol>
 *   <li>调用 {@link AuthenticationManager#authenticate} 进行用户名/密码校验</li>
 *   <li>认证通过后从 {@link SecurityUser} 提取用户信息</li>
 *   <li>签发 Access Token + Refresh Token</li>
 * </ol>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final MenuService menuService;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * 用户登录：校验凭据并签发双 Token。
     *
     * @param request 包含 username + password
     * @return LoginResponse（accessToken, refreshToken, 用户信息）
     * @throws BadCredentialsException 用户名或密码错误
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 构造认证令牌
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());

        // 2. 委托 Spring Security 认证管理器进行认证
        //    → DaoAuthenticationProvider → UserDetailsService.loadUserByUsername()
        //    → BCryptPasswordEncoder.matches()
        Authentication authentication = authenticationManager.authenticate(authToken);

        // 3. 提取认证成功的用户信息
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();

        // 4. 签发 JWT
        String accessToken = jwtUtils.generateAccessToken(securityUser.getUserId(), securityUser.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(securityUser.getUserId(), securityUser.getUsername());

        log.info("用户登录成功: {}", securityUser.getUsername());

        // 5. 构建响应
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .username(securityUser.getUsername())
                .nickname(securityUser.getNickname())
                .avatar(securityUser.getAvatar())
                .build();
    }

    /**
     * 刷新 Access Token。
     *
     * <p>用 Refresh Token 验证用户身份后，签发新的 Access Token。
     * Refresh Token 本身不在此处刷新（简单方案）。</p>
     *
     * @param refreshToken 请求中的 Refresh Token
     * @return 新的 Access Token
     */
    @Override
    public String refreshAccessToken(String refreshToken) {
        // 校验 Refresh Token
        if (!jwtUtils.validateRefreshToken(refreshToken)) {
            log.warn("Refresh Token 无效或已过期");
            throw new BadCredentialsException("Refresh Token 无效或已过期");
        }

        // 从 Token 中提取用户信息并签发新的 Access Token
        Long userId = jwtUtils.getUserId(refreshToken);
        String username = jwtUtils.getUsername(refreshToken);

        log.info("刷新 Access Token: userId={}, username={}", userId, username);
        return jwtUtils.generateAccessToken(userId, username);
    }

    /**
     * 用户登出。
     *
     * <p>将当前 Access Token 加入 Redis 黑名单（剩余有效期作为 TTL），
     * 认证过滤器后续校验到该 Token 时直接拒绝，实现登出即时失效。</p>
     *
     * @param accessToken 当前请求的 Access Token
     */
    @Override
    public void logout(String accessToken) {
        // 将 Access Token 加入 Redis 黑名单（TTL 为其剩余有效期），过滤后续请求
        tokenBlacklistService.blacklist(accessToken);
        String username = jwtUtils.getUsername(accessToken);
        log.info("用户登出: {}", username);
    }

    /**
     * 获取当前登录用户信息：从 SecurityContext 取认证主体，
     * 聚合角色编码、权限标识、菜单树。
     */
    @Override
    public UserInfoResponse getCurrentUserInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityUser su)) {
            throw new RuntimeException("未登录或认证信息缺失");
        }
        // 从 authorities 中提取角色编码（ROLE_ 前缀）
        List<String> roles = su.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .toList();
        // 当前用户可访问的菜单树（仅目录/菜单类型）
        List<MenuEntity> menus = menuService.getCurrentUserMenuTree();

        return UserInfoResponse.builder()
                .id(su.getUserId())
                .username(su.getUsername())
                .nickname(su.getNickname())
                .avatar(su.getAvatar())
                .roles(roles)
                .permissions(su.getPermissions())
                .menus(menus)
                .build();
    }
}
