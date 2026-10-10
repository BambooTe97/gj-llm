package com.gj.llm.base.service.impl;

import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.base.config.WebProperties;
import com.gj.llm.base.entity.MenuEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.event.LogininforEvent;
import com.gj.llm.base.model.ChangePasswordRequest;
import com.gj.llm.base.model.LoginRequest;
import com.gj.llm.base.model.LoginResponse;
import com.gj.llm.base.model.OnlineUserRecord;
import com.gj.llm.base.model.UserInfoResponse;
import com.gj.llm.base.service.AuthService;
import com.gj.llm.base.service.CaptchaService;
import com.gj.llm.base.service.MenuService;
import com.gj.llm.base.service.OnlineUserService;
import com.gj.llm.base.service.UserService;
import com.gj.llm.base.util.WebUtils;
import com.gj.llm.common.exception.ErrorBusinessException;
import com.gj.llm.common.exception.WarnBusinessException;
import com.gj.llm.common.http.UserAgentUtils;
import com.gj.llm.redis.constant.CacheConstants;
import com.gj.llm.redis.service.RedisService;
import com.gj.llm.security.model.SecurityUser;
import com.gj.llm.security.service.TokenBlacklistService;
import com.gj.llm.security.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务实现 —— 处理登录、Token 刷新、登出等核心认证逻辑。
 *
 * <h3>登录流程</h3>
 * <ol>
 *   <li>失败锁定前置检查（Redis {@code login:lock:{username}}）</li>
 *   <li>调用 {@link AuthenticationManager#authenticate} 进行用户名/密码校验，
 *       失败则累计失败计数（滑动窗口），达到上限锁定账号</li>
 *   <li>认证通过后从 {@link SecurityUser} 提取用户信息</li>
 *   <li>签发 Access Token + Refresh Token，并注册在线会话（{@code online:token:{refreshJti}}）</li>
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
    private final RedisService redisService;
    private final AuthProperties authProperties;
    private final WebProperties webProperties;
    private final OnlineUserService onlineUserService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final ApplicationEventPublisher eventPublisher;
    private final MessageSource messageSource;

    /**
     * 用户登录：校验凭据（含防暴力破解）并签发双 Token。
     *
     * @param request      包含 username + password
     * @param httpRequest  当前请求（采集登录 IP / User-Agent）
     * @return LoginResponse（accessToken, refreshToken, 用户信息）
     * @throws BadCredentialsException 用户名或密码错误
     */
    @Override
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername();

        // 0. 滑动验证码校验（开关关闭时内部直接跳过；验证成功即消费令牌）
        captchaService.verify(request.getCaptchaToken(), request.getSlideX());

        // 1. 失败锁定前置检查：锁定期内直接拒绝（BusinessException → 400 + 自定义文案）
        checkLocked(username, httpRequest);

        // 2. 构造认证令牌并委托 Spring Security 认证
        //    → DaoAuthenticationProvider → UserDetailsService.loadUserByUsername()
        //    → BCryptPasswordEncoder.matches()
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(username, request.getPassword());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(authToken);
        } catch (AuthenticationException e) {
            handleLoginFailure(username, httpRequest);
            throw e;
        }

        // 3. 认证成功：清除失败计数
        redisService.delete(CacheConstants.LOGIN_FAIL_KEY + username);

        // 4. 提取认证成功的用户信息并签发 JWT
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        String accessToken = jwtUtils.generateAccessToken(securityUser.getUserId(), securityUser.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(securityUser.getUserId(), securityUser.getUsername());

        // 5. 注册在线会话（key = refresh token 的 jti，TTL = refresh 剩余有效期）
        registerOnlineSession(securityUser, accessToken, refreshToken, httpRequest);

        // 6. 登录日志（异步落库）
        publishLogininfor(securityUser.getUsername(), securityUser.getUserId(),
                "登录成功", LogininforEvent.STATUS_SUCCESS, httpRequest);

        log.info("用户登录成功: {}", securityUser.getUsername());

        // 6. 构建响应
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
        // 黑名单前置检查：强制下线时 refresh token 已入黑名单，
        // 不检查则被踢用户的 refresh token 仍能继续换新（/api/auth/refresh 不经过认证过滤器）
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            log.warn("Refresh Token 已被拉黑（登出/强制下线），拒绝刷新");
            throw new WarnBusinessException("auth.tokenInvalid");
        }

        // 校验 Refresh Token
        if (!jwtUtils.validateRefreshToken(refreshToken)) {
            log.warn("Refresh Token 无效或已过期");
            throw new BadCredentialsException("Refresh Token 无效或已过期");
        }

        // 会话存在性校验：登出/强制下线时在线会话条目已删除。登记表是会话唯一真相，
        // 条目不在则即使 refresh token 仍在有效期内也拒绝，防止登出后凭残留 token 复活会话
        String jti = jwtUtils.getJti(refreshToken);
        if (onlineUserService.findByTokenId(jti) == null) {
            log.warn("Refresh Token 对应的在线会话不存在（已登出/被下线）: jti={}", jti);
            throw new WarnBusinessException("auth.sessionInvalid");
        }

        // 从 Token 中提取用户信息并签发新的 Access Token
        Long userId = jwtUtils.getUserId(refreshToken);
        String username = jwtUtils.getUsername(refreshToken);
        String newAccessToken = jwtUtils.generateAccessToken(userId, username);

        // 原地更新在线会话的 accessToken（key/tokenId 保持稳定）
        onlineUserService.updateAccessToken(refreshToken, newAccessToken);

        log.info("刷新 Access Token: userId={}, username={}", userId, username);
        return newAccessToken;
    }

    /**
     * 用户登出。
     *
     * <p>双 Token 均加入 Redis 黑名单（剩余有效期作为 TTL）——只拉黑 Access Token 会被
     * 7 天有效的 Refresh Token 继续换新导致会话复活（与强制下线同口径）；
     * 随后移除在线会话注册表中的对应条目。</p>
     *
     * @param accessToken 当前请求的 Access Token
     */
    @Override
    public void logout(String accessToken) {
        // Access Token 加入黑名单，过滤后续请求
        tokenBlacklistService.blacklist(accessToken);
        // 定位在线会话：Refresh Token 一并拉黑后移除条目；
        // 会话条目不存在（已过期/Redis 丢失）时仅兜底拉黑 Access Token
        OnlineUserRecord record = onlineUserService.findByAccessToken(accessToken);
        if (record != null) {
            tokenBlacklistService.blacklist(record.getRefreshToken());
            onlineUserService.remove(record.getTokenId());
        }
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
            throw new WarnBusinessException("auth.notAuthenticated");
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

    /**
     * 自助修改密码：原密码确认 → 新密码复杂度策略校验 → 更新并失效安全用户缓存。
     *
     * <p>复用 {@code UserService#resetPassword}（内部已走 {@code PasswordPolicyValidator}
     * 校验与 UserChangedEvent 缓存失效）。当前 Access Token 不强制下线，保留至自然过期。</p>
     */
    @Override
    public void changePassword(String username, ChangePasswordRequest request) {
        UserEntity user = userService.findByUsername(username);
        if (user == null) {
            throw new ErrorBusinessException("user.notFound", username);
        }
        // 原密码确认（失败文案与登录失败口径一致，不暴露额外信息）
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BadCredentialsException("原密码错误");
        }
        userService.resetPassword(user.getId(), request.getNewPassword());
        log.info("用户自助修改密码成功: {}", username);
    }

    // ==================== 登录防暴力破解 ====================

    /**
     * 锁定前置检查：锁定期内直接拒绝并提示剩余时间。
     */
    private void checkLocked(String username, HttpServletRequest request) {
        String lockKey = CacheConstants.LOGIN_LOCK_KEY + username;
        if (redisService.hasKey(lockKey)) {
            long ttlSeconds = Math.max(redisService.getExpire(lockKey), 0);
            long minutes = (ttlSeconds + 59) / 60;
            publishLogininfor(username, null, "账号锁定中", LogininforEvent.STATUS_FAILURE, request);
            throw new WarnBusinessException("auth.accountLocked", minutes);
        }
    }

    /**
     * 登录失败处理：累计失败计数（滑动窗口），达到上限锁定账号。
     *
     * <p>计数窗口滑动刷新：每次失败都重置 TTL，避免进程在 increment 后
     * 崩溃留下永久无 TTL 的计数 key。</p>
     */
    private void handleLoginFailure(String username, HttpServletRequest request) {
        int maxFailures = authProperties.getMaxLoginFailures();
        // 0=不启用锁定
        if (maxFailures <= 0) {
            publishLogininfor(username, null, "用户名或密码错误", LogininforEvent.STATUS_FAILURE, request);
            return;
        }
        Duration window = Duration.ofMinutes(authProperties.getLockDurationMinutes());
        String failKey = CacheConstants.LOGIN_FAIL_KEY + username;
        long fails = redisService.increment(failKey);
        redisService.expire(failKey, window);
        if (fails >= maxFailures) {
            redisService.set(CacheConstants.LOGIN_LOCK_KEY + username, "1", window);
            redisService.delete(failKey);
            // 登录日志落库需可读文案，此处按当前 Locale 直接解析（请求线程内）
            String msg = messageSource.getMessage("auth.lockTriggered",
                    new Object[]{maxFailures, authProperties.getLockDurationMinutes()},
                    LocaleContextHolder.getLocale());
            publishLogininfor(username, null, msg, LogininforEvent.STATUS_FAILURE, request);
            throw new WarnBusinessException("auth.lockTriggered", maxFailures, authProperties.getLockDurationMinutes());
        }
        publishLogininfor(username, null, "用户名或密码错误", LogininforEvent.STATUS_FAILURE, request);
    }

    /**
     * 发布登录日志事件：IP / UA 在请求线程快照进事件（异步线程无请求上下文）。
     */
    private void publishLogininfor(String username, Long userId, String msg,
                                   int status, HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        eventPublisher.publishEvent(new LogininforEvent(
                username,
                userId,
                WebUtils.getClientIp(request, webProperties.isTrustXff()),
                UserAgentUtils.getBrowser(userAgent),
                UserAgentUtils.getOperatingSystem(userAgent),
                status,
                msg,
                LocalDateTime.now()));
    }

    // ==================== 在线会话注册表 ====================

    /**
     * 注册在线会话：记录登录 IP / 浏览器 / 操作系统 / 双 Token。
     */
    private void registerOnlineSession(SecurityUser securityUser, String accessToken,
                                       String refreshToken, HttpServletRequest request) {
        OnlineUserRecord record = OnlineUserRecord.builder()
                .tokenId(jwtUtils.getJti(refreshToken))
                .userId(securityUser.getUserId())
                .username(securityUser.getUsername())
                .nickname(securityUser.getNickname())
                .ip(WebUtils.getClientIp(request, webProperties.isTrustXff()))
                .browser(UserAgentUtils.getBrowser(request.getHeader("User-Agent")))
                .os(UserAgentUtils.getOperatingSystem(request.getHeader("User-Agent")))
                .loginTime(LocalDateTime.now())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
        onlineUserService.register(record);
    }
}
