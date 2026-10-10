package com.gj.llm.base.interceptor;

import com.gj.llm.base.exception.ApiAccessDeniedException;
import com.gj.llm.base.init.ApiPermissionCache;
import com.gj.llm.security.model.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;

/**
 * 接口权限拦截器 -- 统一校验当前请求的接口是否在用户权限范围内，替代 {@code @PreAuthorize} 注解。
 *
 * <h3>校验规则</h3>
 * <ol>
 *   <li>白名单路径（/open/**、login/refresh/userinfo/logout）直接放行</li>
 *   <li>非 Controller 请求（静态资源）放行</li>
 *   <li>当前用户为 ADMIN 角色时全放行</li>
 *   <li>匹配接口（{@link ApiPermissionCache#matchApi}），未匹配则放行</li>
 *   <li>接口未配置权限点 -> 非 admin 拒绝（默认拒绝）</li>
 *   <li>用户持有接口任一权限点 -> 放行，否则拒绝</li>
 * </ol>
 *
 * <p>拒绝时不自行写响应体：抛出 {@link ApiAccessDeniedException} 交由
 * {@code GlobalExceptionHandler} 统一渲染 403 + {@code R}，序列化走全局链路，
 * 与其它接口响应结构一致。本类不持有任何 JSON 依赖。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiPermissionInterceptor implements HandlerInterceptor {

    private final ApiPermissionCache cache;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 免权限校验路径（/open 与 login/refresh/captcha 免登录；userinfo/logout/change-password 需登录免权限） */
    private static final List<String> WHITELIST = List.of(
            "/open/**",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/userinfo",
            "/api/auth/logout",
            "/api/auth/change-password",
            "/api/captcha/**"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // 1. 白名单放行
        if (isWhitelisted(path)) {
            return true;
        }

        // 2. 非 Controller 请求（静态资源等）放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 3. 取当前登录用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityUser su)) {
            // 未认证：SecurityConfig 已拦截 authenticated 路径，到此处说明白名单遗漏，交由 Security 兜底
            return true;
        }

        // 4. ADMIN 角色全放行
        if (su.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return true;
        }

        // 5. 匹配接口
        Long apiId = cache.matchApi(method, path);
        if (apiId == null) {
            // 未扫描入库的接口，放行
            return true;
        }

        // 6. 接口权限点校验（拒绝 -> 抛异常，由全局异常处理器渲染 403）
        Set<String> perms = cache.getPerms(apiId);
        if (perms.isEmpty()) {
            // 接口未配置权限点 -> 默认拒绝（非 admin）
            throw new ApiAccessDeniedException("auth.apiNotOpen");
        }
        if (perms.stream().anyMatch(p -> su.getPermissions().contains(p))) {
            return true;
        }

        throw new ApiAccessDeniedException("auth.accessDenied");
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(p -> pathMatcher.match(p, path));
    }
}
