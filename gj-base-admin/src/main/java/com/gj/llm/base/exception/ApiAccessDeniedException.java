package com.gj.llm.base.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * 接口权限拒绝异常 -- {@code ApiPermissionInterceptor} 校验失败时抛出（已认证但无权访问），
 * 由 {@code GlobalExceptionHandler} 统一渲染为 HTTP 403 + {@code R}（含 level/bizCode）。
 *
 * <p>继承 {@link AccessDeniedException} 而非 {@code BusinessException}：语义上是权限问题（403）
 * 而非业务失败（400），并归入异常处理器的安全异常分支。携带 bizCode（i18n key）以区分
 * "接口未开放"（{@code auth.apiNotOpen}）与"权限不足"（{@code auth.accessDenied}）。</p>
 *
 * @author gj-llm
 */
public class ApiAccessDeniedException extends AccessDeniedException {

    /** 业务码（即 i18n key），供前端埋点或特定业务码处理 */
    private final String bizCode;

    public ApiAccessDeniedException(String bizCode) {
        super(bizCode);
        this.bizCode = bizCode;
    }

    public String getBizCode() {
        return bizCode;
    }
}
