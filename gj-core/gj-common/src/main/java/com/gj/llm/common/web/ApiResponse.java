package com.gj.llm.common.web;

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
public class ApiResponse<T> {

    /** 业务状态码：200=成功，400=参数错误，401=未认证，403=无权限，500=服务端错误 */
    private int code;

    /** 响应数据 */
    private T data;

    /** 提示信息 */
    private String message;

    // ==================== 静态工厂方法 ====================

    /** 成功响应（带数据） */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, data, "操作成功");
    }

    /** 成功响应（带数据和自定义消息） */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(200, data, message);
    }

    /** 失败响应 */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, null, message);
    }

    /** 参数错误 (400) */
    public static <T> ApiResponse<T> badRequest(String message) {
        return fail(400, message);
    }

    /** 未认证 (401) */
    public static <T> ApiResponse<T> unauthorized(String message) {
        return fail(401, message);
    }

    /** 无权限 (403) */
    public static <T> ApiResponse<T> forbidden(String message) {
        return fail(403, message);
    }

    /** 服务器错误 (500) */
    public static <T> ApiResponse<T> error(String message) {
        return fail(500, message);
    }
}
