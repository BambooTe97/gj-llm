package com.gj.llm.base.service.impl;

import com.gj.llm.base.model.LoginRequest;
import com.gj.llm.base.model.LoginResponse;
import com.gj.llm.base.service.AuthService;
import com.gj.llm.security.model.SecurityUser;
import com.gj.llm.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

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
     * <p>当前为无状态 JWT 模式，服务端无需操作（客户端清除 Token 即可）。
     * 后续若引入 Redis Token 黑名单，在此处将当前 Token 加入黑名单。</p>
     *
     * @param accessToken 当前请求的 Access Token
     */
    @Override
    public void logout(String accessToken) {
        // 无状态 JWT：服务端无需额外操作
        String username = jwtUtils.getUsername(accessToken);
        log.info("用户登出: {}", username);
    }
}
