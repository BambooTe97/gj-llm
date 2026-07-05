package com.gj.llm.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

/**
 * Spring Security 工具类 —— 提供获取当前登录用户等安全相关快捷方法。
 *
 * <p>当无法获取认证信息时（如未登录、定时任务、测试环境），
 * 统一返回 {@code "anonymous"}，避免 NPE。</p>
 *
 * @author gj-llm
 */
public final class SecurityUtils {

    /** 匿名用户标识 */
    public static final String ANONYMOUS = "anonymous";

    private SecurityUtils() {
        // 工具类禁止实例化
    }

    /**
     * 获取当前登录用户名。
     *
     * <p>优先从 {@link UserDetails} 获取 username；若不是 UserDetails 实例，
     * 则回退到 {@link Authentication#getName()}；捕获不到认证信息时返回 "anonymous"。</p>
     *
     * @return 当前用户名，获取失败返回 {@code "anonymous"}
     */
    public static String getCurrentUsername() {
        return getAuthentication()
                .map(SecurityUtils::extractUsername)
                .orElse(ANONYMOUS);
    }

    /**
     * 获取当前认证信息。
     *
     * @return 认证信息 Optional，未认证时为 {@link Optional#empty()}
     */
    public static Optional<Authentication> getAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated);
    }

    /**
     * 判断当前请求是否已认证。
     *
     * @return true 表示已登录
     */
    public static boolean isAuthenticated() {
        return getAuthentication().isPresent();
    }

    // ==================== 私有方法 ====================

    private static String extractUsername(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        // principal 可能是 String 类型（如 anonymousUser）
        return principal.toString();
    }
}
