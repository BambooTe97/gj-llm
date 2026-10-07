package com.gj.llm.base.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解 -- 标注在需要审计的 Controller 方法上，由 {@code OperLogAspect} 切面拦截，
 * 采集模块/类型/请求参数/返回结果/耗时等信息，异步落库到 {@code sys_oper_log}。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @OperLog(module = "用户管理", type = "新增")
 * @PostMapping
 * public R<UserEntity> create(@Valid @RequestBody UserCreateRequest request) { ... }
 * }</pre>
 *
 * <p>敏感字段（password/token 等）在参数与结果序列化时统一脱敏；
 * 日志落库为旁路异步操作，失败只记 WARN 不影响业务。</p>
 *
 * @author gj-llm
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 操作模块，如 用户管理 */
    String module();

    /** 操作类型，如 新增/更新/删除/登录 */
    String type();
}
