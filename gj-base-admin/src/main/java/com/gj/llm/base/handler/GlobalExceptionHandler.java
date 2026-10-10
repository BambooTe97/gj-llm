package com.gj.llm.base.handler;

import com.gj.llm.base.exception.ApiAccessDeniedException;
import com.gj.llm.common.exception.BusinessException;
import com.gj.llm.common.web.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 -- 统一处理参数校验、安全认证、业务异常，以 {@link R} 格式返回。
 *
 * <p>将 Security 异常与业务异常合并到同一个 {@code @RestControllerAdvice} 中，
 * 确保 Spring MVC 按异常类型精确匹配（而非跨类模糊匹配）。</p>
 *
 * <h3>映射边界</h3>
 * <ul>
 *   <li>{@link BusinessException}：service 层可预期的业务失败 → 400 + i18n 文案，
 *       并按异常子类携带 level（error/warn）与 bizCode（i18n key）供前端分级呈现</li>
 *   <li>{@link DataAccessException}：数据访问异常不是业务异常 → 500 + 通用文案，
 *       避免表名/SQL 片段外泄（唯一键冲突单独映射为 400 友好文案）</li>
 *   <li>其余未知异常 → 500 兜底</li>
 * </ul>
 *
 * <p>文案统一按请求 Locale（Accept-Language，缺省 zh_CN）解析；动态拼装的
 * 校验信息（字段名、详情参数）保持原样，key 缺失时兜底返回 key 本身并记录
 * error 日志，便于暴露缺词条。</p>
 *
 * @author gj-llm
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // ==================== 参数校验 ====================

    /**
     * 处理 {@code @Valid} 参数校验失败异常。
     * 将所有字段错误合并为一条提示信息。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return R.badRequest(message);
    }

    /**
     * 请求参数缺失 / 类型不匹配 / 请求体不可读 —— 客户端调用姿势问题，400。
     */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBadRequest(Exception e) {
        log.warn("请求参数错误: {}", e.getMessage());
        return R.badRequest(msg("common.requestParamError"));
    }

    /**
     * Handler 方法参数内置校验失败（如分页 size 上的 {@code @Max(200)}）—— 400。
     * Spring 6.1 起对 handler 方法参数上的约束注解自动启用校验，无需 {@code @Validated}。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        String message = Arrays.stream(e.getDetailMessageArguments())
                .map(String::valueOf)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return R.badRequest(message);
    }

    /**
     * HTTP 方法不支持 —— 405。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("HTTP 方法不支持: {}", e.getMessage());
        return R.fail(405, msg("common.methodNotSupported"));
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
    public R<Void> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("凭据错误: {}", e.getMessage());
        return R.badRequest(msg("auth.badCredentials"));
    }

    /**
     * 认证失败 —— Token 无效、过期等非登录场景的认证异常。
     * 返回 401，前端拦截器据此跳转登录页。
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return R.unauthorized(msg("auth.notAuthenticated401"));
    }

    /**
     * 权限不足 —— 已认证但无访问权限。
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return R.forbidden(msg("auth.accessDenied"));
    }

    /**
     * 接口权限拦截器拒绝 —— {@code ApiPermissionInterceptor} 校验失败（接口未开放
     * 或未持有任一权限点）。与 {@link #handleAccessDeniedException} 的差异仅在
     * 透传 bizCode（i18n key），供前端区分"接口未开放"与"权限不足"。
     */
    @ExceptionHandler(ApiAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleApiAccessDenied(ApiAccessDeniedException e) {
        log.warn("接口权限拒绝: {}", e.getBizCode());
        R<Void> body = R.forbidden(msg(e.getBizCode()));
        body.setLevel(BusinessException.LEVEL_ERROR);
        body.setBizCode(e.getBizCode());
        return body;
    }

    // ==================== 业务 & 数据访问 ====================

    /**
     * 业务异常（如 "用户不存在"、"用户名已存在" 等）—— service 层显式抛出。
     * 按 i18n key 解析文案，并透传异常子类决定的 level（error/warn）与 bizCode。
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: key={}, args={}", e.getI18nKey(), e.getArgs());
        return R.business(e.getLevel(), e.getI18nKey(), resolve(e));
    }

    /**
     * 唯一键冲突 —— check-then-insert 挡不住的并发重复提交，映射为 400 友好文案。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return R.badRequest(msg("common.duplicateSubmit"));
    }

    /**
     * 其余数据访问异常（SQL 错误、连接异常等）—— 编号 500，通用文案，
     * 全栈只进日志不返回客户端（避免表名/SQL 片段外泄）。
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleDataAccess(DataAccessException e) {
        log.error("数据访问异常", e);
        return R.error(msg("common.serverError"));
    }

    /**
     * 兜底处理：未知异常统一返回 500。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.error(msg("common.serverError"));
    }

    /**
     * 按当前 Locale 解析业务异常文案；key 缺失时返回 key 本身（暴露缺词条），
     * 绝不让处理器二次抛异常。
     */
    private String resolve(BusinessException e) {
        try {
            return messageSource.getMessage(e.getI18nKey(), e.getArgs(), LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException ex) {
            log.error("缺失 i18n key: {}", e.getI18nKey());
            return e.getI18nKey();
        }
    }

    /** 解析固定 key 文案（无占位参数），兜底策略同 {@link #resolve}。 */
    private String msg(String key) {
        try {
            return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException ex) {
            log.error("缺失 i18n key: {}", key);
            return key;
        }
    }
}
