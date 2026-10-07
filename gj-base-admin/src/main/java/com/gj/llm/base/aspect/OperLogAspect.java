package com.gj.llm.base.aspect;

import com.gj.llm.base.annotation.OperLog;
import com.gj.llm.base.event.OperLogEvent;
import com.gj.llm.base.util.WebUtils;
import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Pattern;

/**
 * 操作日志切面 -- 拦截 {@link OperLog} 注解的方法，采集审计信息后发布 {@link OperLogEvent}，
 * 由监听器异步落库（旁路，不阻塞业务主链路）。
 *
 * <h3>采集内容</h3>
 * <ul>
 *   <li>操作人 / IP：请求线程快照（异步线程无 SecurityContext）</li>
 *   <li>请求参数 / 返回结果：JSON 序列化，敏感字段脱敏，超长截断</li>
 *   <li>成败状态 / 错误消息 / 耗时</li>
 * </ul>
 *
 * @author gj-llm
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    /** 参数/结果 JSON 截断长度 */
    private static final int MAX_JSON_LENGTH = 2000;

    /** 错误消息截断长度 */
    private static final int MAX_ERROR_LENGTH = 1000;

    /** 敏感字段脱敏（参数与结果统一处理），如 "password":"xxx" → "password":"***" */
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "\"(password|newPassword|oldPassword|accessToken|refreshToken|token|authorization|secret|apiKey)\"\\s*:\\s*\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private static final String MASK = "\"$1\":\"***\"";

    private final ApplicationEventPublisher eventPublisher;

    @Around(value = "@annotation(operLog)", argNames = "joinPoint,operLog")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        String method = joinPoint.getTarget().getClass().getSimpleName() + "." + joinPoint.getSignature().getName();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs == null ? null : attrs.getRequest();
        String requestUri = request == null ? "" : request.getRequestURI();
        String requestMethod = request == null ? "" : request.getMethod();
        String ip = WebUtils.getClientIp(request);
        // 操作人快照：异步线程无 SecurityContext，必须在请求线程上取
        String operator = SecurityUtils.getCurrentUsername();
        Long userId = SecurityUtils.getCurrentUserId();
        String params = serializeArgs(joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            publishEvent(operLog, method, requestUri, requestMethod, operator, userId, ip, params,
                    truncate(maskSensitive(toJsonSafe(result))), OperLogEvent.STATUS_SUCCESS, null,
                    System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            publishEvent(operLog, method, requestUri, requestMethod, operator, userId, ip, params,
                    null, OperLogEvent.STATUS_FAILURE,
                    truncate(e.getMessage(), MAX_ERROR_LENGTH),
                    System.currentTimeMillis() - start);
            throw e;
        }
    }

    private void publishEvent(OperLog operLog, String method, String requestUri, String requestMethod,
                              String operator, Long userId, String ip, String params, String result,
                              int status, String errorMsg, long costMs) {
        try {
            OperLogEvent event = new OperLogEvent(operLog.module(), operLog.type(), method, requestUri,
                    requestMethod, operator, userId, ip, params, result, status, errorMsg, costMs);
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("[操作日志] 事件发布失败: module={}, type={}", operLog.module(), operLog.type(), e);
        }
    }

    /**
     * 序列化请求参数：跳过非业务类型（Servlet 对象/文件等），敏感字段脱敏，超长截断。
     */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object[] filtered = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            filtered[i] = isBusinessArg(args[i]) ? args[i] : String.valueOf(args[i]);
        }
        return truncate(maskSensitive(toJsonSafe(filtered)));
    }

    private boolean isBusinessArg(Object arg) {
        return !(arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                || arg instanceof MultipartFile || arg instanceof byte[]);
    }

    private String toJsonSafe(Object value) {
        try {
            return JacksonUtils.toJson(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** 敏感字段脱敏 */
    private String maskSensitive(String json) {
        return json == null ? null : SENSITIVE_FIELD_PATTERN.matcher(json).replaceAll(MASK);
    }

    private String truncate(String json) {
        return truncate(json, MAX_JSON_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
