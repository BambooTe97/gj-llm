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
 * 登录日志实体 -- 每次登录尝试（成功/失败/锁定拒绝）一条，由监听器异步落库。
 *
 * <p>日志表不继承 BaseEntity（异步线程无安全上下文，create_by 自动填充无意义），
 * loginTime 由事件发布方在请求线程赋值。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_logininfor")
public class LogininforEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录账号（尝试值，可能不存在） */
    private String username;

    /** 用户 ID（可空：失败尝试的用户可能不存在） */
    private Long userId;

    /** 客户端 IP */
    private String ip;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 登录状态：1=成功 0=失败 */
    private Integer status;

    /** 提示消息 */
    private String msg;

    /** 登录时间 */
    private LocalDateTime loginTime;
}
