package com.gj.llm.base.event;

import java.time.LocalDateTime;

/**
 * 登录日志事件 -- 由 {@code AuthServiceImpl} 在登录成功/失败/锁定拒绝路径上直接发布
 * （不走 @OperLog 切面：失败时认证主体与出入参在切面里拿不全），
 * {@code LogininforEventListener} 异步消费落库。
 *
 * <p>IP 与 UA 在请求线程快照进事件（异步线程无请求上下文）。</p>
 *
 * @param username  登录账号（尝试值，可能不存在）
 * @param userId    用户 ID（可空：失败尝试的用户可能不存在）
 * @param ip        客户端 IP（快照）
 * @param browser   浏览器（UA 解析快照）
 * @param os        操作系统（UA 解析快照）
 * @param status    登录状态：1=成功 0=失败
 * @param msg       提示消息（登录成功/用户名或密码错误/账号锁定中...）
 * @param loginTime 登录时间（请求线程快照）
 *
 * @author gj-llm
 */
public record LogininforEvent(
        String username,
        Long userId,
        String ip,
        String browser,
        String os,
        Integer status,
        String msg,
        LocalDateTime loginTime) {

    /** 登录状态：成功 */
    public static final int STATUS_SUCCESS = 1;

    /** 登录状态：失败 */
    public static final int STATUS_FAILURE = 0;
}
