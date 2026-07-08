package com.gj.llm.base.handler;

import com.gj.llm.base.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 统一处理参数校验、安全认证、业务异常，以 {@link ApiResponse} 格式返回。
 *
 * <p>将 Security 异常与业务异常合并到同一个 {@code @RestControllerAdvice} 中，
 * 确保 Spring MVC 按异常类型精确匹配（而非跨类模糊匹配）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 参数校验 ====================

    /**
     * 处理 {@code @Valid} 参数校验失败异常。
     * 将所有字段错误合并为一条提示信息。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return ApiResponse.badRequest(message);
    }

    // ==================== 安全认证 ====================

    /**
     * 凭据错误 —— 用户名或密码错误。
     * Spring Security 的 {@code DaoAuthenticationProvider} 在校验密码失败或用户不存在时抛出。
     * <p>
     * 使用 HTTP 400（业务错误）而非 401，以便前端区分"登录失败"和"Token 过期"两种场景。
     * 返回固定提示语，不泄露 Spring Security 原始异常信息。
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("凭据错误: {}", e.getMessage());
        return ApiResponse.badRequest("用户名或密码错误");
    }

    /**
     * 认证失败 —— Token 无效、过期等非登录场景的认证异常。
     * 返回 401，前端拦截器据此跳转登录页。
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return ApiResponse.unauthorized("认证失败，请重新登录");
    }

    /**
     * 权限不足 —— 已认证但无访问权限。
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return ApiResponse.forbidden("权限不足");
    }

    // ==================== 业务 & 兜底 ====================

    /**
     * 处理业务异常（如 "用户不存在"、"用户名已存在" 等）。
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ApiResponse.badRequest(e.getMessage());
    }

    /**
     * 兜底处理：未知异常统一返回 500。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error("服务器内部错误");
    }
}
