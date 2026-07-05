package com.gj.llm.security.filter;

import com.gj.llm.security.config.SecurityConfig;
import com.gj.llm.security.model.SecurityUser;
import com.gj.llm.security.service.SecurityUserService;
import com.gj.llm.security.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * JWT 认证过滤器 —— 在每次 HTTP 请求到达 Controller 前拦截，从 Authorization 头中提取
 * Bearer Token 并校验其有效性，校验通过后将认证信息写入 Spring Security 上下文。
 *
 * <p>继承 {@link OncePerRequestFilter} 确保每个请求只过滤一次。
 * 该过滤器在 {@link SecurityConfig#securityFilterChain} 中注册，
 * 执行顺序位于 {@code UsernamePasswordAuthenticationFilter} 之前。</p>
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>从 {@code Authorization: Bearer <token>} 头提取 JWT</li>
 *   <li>校验 Access Token 签名与过期时间</li>
 *   <li>从 Token 中解析用户信息</li>
 *   <li>通过 {@link SecurityUserService} 加载完整用户对象</li>
 *   <li>构造 {@link UsernamePasswordAuthenticationToken} 写入 {@link SecurityContextHolder}</li>
 * </ol>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** HTTP Authorization 头的 Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final SecurityUserService securityUserService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. 从请求头提取 JWT
        String token = extractToken(request);
        if (token == null) {
            // 无 Token，直接放行交给后续鉴权规则处理（通常返回 401）
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 校验 Access Token
        if (!jwtUtils.validateAccessToken(token)) {
            log.debug("请求携带的 Access Token 无效或已过期");
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 解析 Token 中的用户标识
        Long userId = jwtUtils.getUserId(token);
        String username = jwtUtils.getUsername(token);

        // 4. 加载完整用户信息
        Optional<SecurityUser> userOpt = securityUserService.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.debug("Token 中的用户已不存在: {}", username);
            filterChain.doFilter(request, response);
            return;
        }

        SecurityUser securityUser = userOpt.get();

        // 5. 检查账户是否被禁用
        if (!securityUser.isEnabled()) {
            log.debug("用户已被禁用: {}", username);
            filterChain.doFilter(request, response);
            return;
        }

        // 6. 构造认证对象并写入 SecurityContext
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        securityUser,       // principal: 用户信息
                        null,               // credentials: Token 已验证，无需凭证
                        securityUser.getAuthorities()
                );
        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("JWT 认证成功: userId={}, username={}", userId, username);
        filterChain.doFilter(request, response);
    }

    /**
     * 从 HTTP 请求头中提取 Bearer Token。
     *
     * @param request HTTP 请求
     * @return Token 字符串（不含 "Bearer " 前缀），若头部缺失或格式不正确则返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
