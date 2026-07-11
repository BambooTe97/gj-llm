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
import org.springframework.security.core.context.SecurityContext;
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
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 校验 Access Token
        if (!jwtUtils.validateAccessToken(token)) {
            log.warn("JWT 校验失败, path={}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 解析 Token 中的用户标识
        Long userId = jwtUtils.getUserId(token);
        String username = jwtUtils.getUsername(token);

        // 4. 加载完整用户信息
        Optional<SecurityUser> userOpt = securityUserService.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("Token 中的用户已不存在, username={}", username);
            filterChain.doFilter(request, response);
            return;
        }

        SecurityUser securityUser = userOpt.get();

        // 5. 检查账户是否被禁用
        if (!securityUser.isEnabled()) {
            log.warn("用户已被禁用, username={}", username);
            filterChain.doFilter(request, response);
            return;
        }

        // 6. 构造认证对象并写入 SecurityContext
        // 注意：必须创建新的 SecurityContext 替换旧的，而非原地修改。
        // SecurityContextHolderFilter 通过引用比较（!=）判断是否需要
        // 将 context 持久化到 SecurityContextRepository；
        // 原地修改会导致异步 dispatch 时认证信息丢失。
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        securityUser, null, securityUser.getAuthorities());
        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext newContext = SecurityContextHolder.createEmptyContext();
        newContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(newContext);

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
