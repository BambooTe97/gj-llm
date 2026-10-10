package com.gj.llm.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gj.llm.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应对象 —— 与前端 {@code ApiResponse<T>} 契约对齐。
 *
 * <p>所有 Controller 返回值均使用此类包装，确保前端统一解析。</p>
 *
 * @param <T> 响应数据类型
 * @author gj-llm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> {

    /** 业务状态码：200=成功，400=参数错误，401=未认证，403=无权限，500=服务端错误 */
    private int code;

    /** 响应数据 */
    private T data;

    /** 提示信息 */
    private String message;

    /** 前端提示分级：{@code "error"}=红色通知，{@code "warn"}=黄色提示；null=未分级（兼容旧响应） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String level;

    /** 业务码（即 i18n key），供前端埋点或特定业务码处理；null=未携带 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String bizCode;

    // ==================== 静态工厂方法 ====================

    /** 成功响应（带数据） */
    public static <T> R<T> ok(T data) {
        return new R<>(200, data, "操作成功", null, null);
    }

    /** 成功响应（带数据和自定义消息） */
    public static <T> R<T> ok(T data, String message) {
        return new R<>(200, data, message, null, null);
    }

    /** 失败响应 */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, null, message, null, null);
    }

    /**
     * 业务分级失败响应 (400) —— 由 GlobalExceptionHandler 处理 BusinessException 时使用。
     *
     * @param level   前端提示分级：{@link BusinessException#LEVEL_ERROR} / {@link BusinessException#LEVEL_WARN}
     * @param bizCode 业务码（i18n key）
     * @param message 已解析的可读文案
     */
    public static <T> R<T> business(String level, String bizCode, String message) {
        return new R<>(400, null, message, level, bizCode);
    }

    /** 参数错误 (400) */
    public static <T> R<T> badRequest(String message) {
        return fail(400, message);
    }

    /** 未认证 (401) */
    public static <T> R<T> unauthorized(String message) {
        return fail(401, message);
    }

    /** 无权限 (403) */
    public static <T> R<T> forbidden(String message) {
        return fail(403, message);
    }

    /** 服务器错误 (500) */
    public static <T> R<T> error(String message) {
        return fail(500, message);
    }
}
