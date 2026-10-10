package com.gj.llm.base.service.impl;

import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.base.config.WebProperties;
import com.gj.llm.base.model.LoginRequest;
import com.gj.llm.base.model.OnlineUserRecord;
import com.gj.llm.base.service.CaptchaService;
import com.gj.llm.base.service.MenuService;
import com.gj.llm.base.service.OnlineUserService;
import com.gj.llm.base.service.UserService;
import com.gj.llm.common.exception.BusinessException;
import com.gj.llm.redis.constant.CacheConstants;
import com.gj.llm.redis.service.RedisService;
import com.gj.llm.security.service.TokenBlacklistService;
import com.gj.llm.security.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthServiceImpl} 单测 -- 覆盖会话修复核心场景：
 * logout 双拉黑、refresh 会话存在性校验、登录锁定分支。
 *
 * @author gj-llm
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtils jwtUtils;
    @Mock private MenuService menuService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private RedisService redisService;
    @Mock private OnlineUserService onlineUserService;
    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CaptchaService captchaService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private HttpServletRequest httpRequest;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(authenticationManager, jwtUtils, menuService,
                tokenBlacklistService, redisService, new AuthProperties(), new WebProperties(),
                onlineUserService, userService, passwordEncoder, captchaService, eventPublisher,
                messageSource());
    }

    /** 真实词条 bundle（读 main 资源），与生产同源；断言 i18n key / args 而非解析文案 */
    private static MessageSource messageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("i18n/base_messages");
        ms.setDefaultEncoding("UTF-8");
        return ms;
    }

    // ==================== logout 双拉黑 ====================

    @Test
    void logoutBlacklistsBothTokensAndRemovesSession() {
        OnlineUserRecord record = OnlineUserRecord.builder()
                .tokenId("tid-1")
                .refreshToken("rt-1")
                .build();
        when(onlineUserService.findByAccessToken("at-1")).thenReturn(record);
        when(jwtUtils.getUsername("at-1")).thenReturn("alice");

        authService.logout("at-1");

        InOrder order = inOrder(tokenBlacklistService, onlineUserService);
        order.verify(tokenBlacklistService).blacklist("at-1");
        // refresh token 一并拉黑，防止 7 天内凭残留 refresh 复活会话
        order.verify(tokenBlacklistService).blacklist("rt-1");
        order.verify(onlineUserService).remove("tid-1");
    }

    @Test
    void logoutWithMissingSessionStillBlacklistsAccessToken() {
        // 会话条目不存在（已过期/Redis 丢失）：仅兜底拉黑 Access Token，不误删
        when(onlineUserService.findByAccessToken("at-1")).thenReturn(null);
        when(jwtUtils.getUsername("at-1")).thenReturn("alice");

        authService.logout("at-1");

        verify(tokenBlacklistService, times(1)).blacklist(anyString());
        verify(tokenBlacklistService).blacklist("at-1");
        verify(onlineUserService, never()).remove(any());
    }

    // ==================== refresh 会话存在性校验 ====================

    @Test
    void refreshRejectedWhenSessionEntryMissing() {
        when(tokenBlacklistService.isBlacklisted("rt-1")).thenReturn(false);
        when(jwtUtils.validateRefreshToken("rt-1")).thenReturn(true);
        when(jwtUtils.getJti("rt-1")).thenReturn("jti-1");
        when(onlineUserService.findByTokenId("jti-1")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.refreshAccessToken("rt-1"));

        assertEquals("auth.sessionInvalid", ex.getMessage());
        // 不签发新 token、不更新在线会话
        verify(jwtUtils, never()).generateAccessToken(any(), any());
        verify(onlineUserService, never()).updateAccessToken(any(), any());
    }

    @Test
    void refreshRejectedWhenRefreshTokenBlacklisted() {
        when(tokenBlacklistService.isBlacklisted("rt-1")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.refreshAccessToken("rt-1"));

        assertEquals("auth.tokenInvalid", ex.getMessage());
        verify(jwtUtils, never()).validateRefreshToken(any());
    }

    @Test
    void refreshIssuesNewAccessTokenWhenSessionAlive() {
        when(tokenBlacklistService.isBlacklisted("rt-1")).thenReturn(false);
        when(jwtUtils.validateRefreshToken("rt-1")).thenReturn(true);
        when(jwtUtils.getJti("rt-1")).thenReturn("jti-1");
        when(onlineUserService.findByTokenId("jti-1"))
                .thenReturn(OnlineUserRecord.builder().tokenId("jti-1").build());
        when(jwtUtils.getUserId("rt-1")).thenReturn(1L);
        when(jwtUtils.getUsername("rt-1")).thenReturn("alice");
        when(jwtUtils.generateAccessToken(1L, "alice")).thenReturn("new-at");

        String newToken = authService.refreshAccessToken("rt-1");

        assertTrue("new-at".equals(newToken));
        verify(onlineUserService).updateAccessToken("rt-1", "new-at");
    }

    // ==================== 登录锁定分支 ====================

    @Test
    void loginRejectedWithRemainingLockMinutes() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("whatever");
        String lockKey = CacheConstants.LOGIN_LOCK_KEY + "alice";
        when(redisService.hasKey(lockKey)).thenReturn(true);
        when(redisService.getExpire(lockKey)).thenReturn(120L);
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(request, httpRequest));

        // 120s → 锁定提示携带 2 分钟占位参数
        assertEquals("auth.accountLocked", ex.getI18nKey());
        assertEquals(2L, ex.getArgs()[0]);
        // 锁定期内不走认证管理器
        verify(authenticationManager, never()).authenticate(any());
    }
}
