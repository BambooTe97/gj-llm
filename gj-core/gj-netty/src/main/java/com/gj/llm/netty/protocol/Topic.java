package com.gj.llm.netty.protocol;

import java.util.regex.Pattern;

/**
 * Topic 常量与命名校验 —— 消息路由的命名空间规范。
 *
 * <p>命名格式 {@code <biz>.<event>}（如 {@code chat.content}、{@code notify.task.done}），
 * 前缀 {@code sys.} 为基座保留，业务不得占用。校验规则与 {@code GJ_NETTY_GUIDE.md} 第五节一致。</p>
 *
 * @author gj-llm
 */
public final class Topic {

    /** 基座保留命名空间前缀 */
    public static final String SYS_PREFIX = "sys.";

    /** 心跳消息（服务端写空闲时主动 ping，客户端可回复同名消息刷新读空闲） */
    public static final String SYS_HEARTBEAT = "sys.heartbeat";

    /** 消息回执 */
    public static final String SYS_ACK = "sys.ack";

    /** 客户端订阅下行 topic（payload: {"topic": "chat.123"}） */
    public static final String SYS_SUBSCRIBE = "sys.subscribe";

    /** 客户端取消订阅 */
    public static final String SYS_UNSUBSCRIBE = "sys.unsubscribe";

    /** 优雅停机时服务端向全部连接发出的通知 */
    public static final String SYS_SERVER_SHUTDOWN = "sys.server.shutdown";

    /** 首帧补鉴权（P1 预留，握手鉴权为主通道） */
    public static final String SYS_AUTH = "sys.auth";

    /**
     * topic 命名校验：至少两段，段以点分隔；段为小写字母/数字/下划线（允许数字开头，
     * 支持 {@code chat.42} 这类"业务前缀 + 数字 ID"形态），段首不得是下划线。
     */
    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_]*(\\.[a-z0-9][a-z0-9_]*)+$");

    private Topic() {
    }

    /**
     * 校验 topic 命名是否合法。
     */
    public static boolean isValid(String topic) {
        return topic != null && PATTERN.matcher(topic).matches();
    }

    /**
     * 是否为基座保留的 sys.* 命名空间。
     */
    public static boolean isSystem(String topic) {
        return topic != null && topic.startsWith(SYS_PREFIX);
    }
}
