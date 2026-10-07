package com.gj.llm.base.event;

/**
 * 操作日志事件 -- 由 {@code OperLogAspect} 在请求线程上发布，
 * {@code OperLogEventListener} 异步消费落库。
 *
 * <p>操作人与 IP 在请求线程快照进事件（异步线程无 SecurityContext 与请求上下文）。</p>
 *
 * @param module        操作模块，如 用户管理
 * @param type          操作类型，如 新增
 * @param method        操作方法（类名.方法名）
 * @param requestUri    请求 URI
 * @param requestMethod 请求方式（GET/POST/...）
 * @param operator      操作人用户名（快照）
 * @param userId        操作人 ID（快照，可为 null）
 * @param ip            客户端 IP（快照）
 * @param params        请求参数 JSON（敏感字段已脱敏，超长截断）
 * @param result        返回结果 JSON（超长截断）
 * @param status        操作状态：1=成功 0=失败
 * @param errorMsg      错误消息（失败时）
 * @param costMs        耗时（毫秒）
 *
 * @author gj-llm
 */
public record OperLogEvent(
        String module,
        String type,
        String method,
        String requestUri,
        String requestMethod,
        String operator,
        Long userId,
        String ip,
        String params,
        String result,
        Integer status,
        String errorMsg,
        Long costMs) {

    /** 操作状态：成功 */
    public static final int STATUS_SUCCESS = 1;

    /** 操作状态：失败 */
    public static final int STATUS_FAILURE = 0;
}
