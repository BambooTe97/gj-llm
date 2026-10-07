package com.gj.llm.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志实体 -- @OperLog 切面采集的审计日志，由监听器异步落库。
 *
 * <p>日志表不继承 BaseEntity（异步线程无安全上下文，create_by 自动填充无意义），
 * createdAt 由监听器手动赋值（与 McpAuditLogEntity 同做法）。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_oper_log")
public class OperLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作模块，如 用户管理 */
    private String module;

    /** 操作类型，如 新增/删除/登录 */
    private String type;

    /** 操作方法（类名.方法名） */
    private String method;

    /** 请求 URI */
    private String requestUri;

    /** 请求方式（GET/POST/...） */
    private String requestMethod;

    /** 操作人用户名 */
    private String operator;

    /** 操作人 ID */
    private Long userId;

    /** 客户端 IP */
    private String ip;

    /** 请求参数（JSON，敏感字段已脱敏） */
    private String params;

    /** 返回结果（JSON，截断存储） */
    private String result;

    /** 操作状态：1=成功 0=失败 */
    private Integer status;

    /** 错误消息（失败时，截断存储） */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
