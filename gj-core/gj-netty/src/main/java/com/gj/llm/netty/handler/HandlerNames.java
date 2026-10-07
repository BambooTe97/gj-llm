package com.gj.llm.netty.handler;

/**
 * 内置 handler 在 pipeline 中的注册名 —— {@code PipelineCustomizer} 的挂载锚点。
 *
 * <p>装配顺序（入站方向）：
 * {@code codec → aggregator → gjHandshakeAuth → [WS protocol] → gjSessionLifecycle →
 * gjIdleHeartbeat → gjInboundRateLimit → gjMessageDecoder → (CODEC 定制区) →
 * gjTopicDispatch → (DISPATCH 定制区) → gjExceptionTracker}</p>
 *
 * @author gj-llm
 */
public final class HandlerNames {

    /** 握手鉴权（HANDSHAKE 阶段锚点） */
    public static final String HANDSHAKE_AUTH = "gjHandshakeAuth";

    /** 会话生命周期 */
    public static final String SESSION_LIFECYCLE = "gjSessionLifecycle";

    /** 空闲心跳（半开连接清理） */
    public static final String IDLE_HEARTBEAT = "gjIdleHeartbeat";

    /** 上行限速 */
    public static final String INBOUND_RATE_LIMIT = "gjInboundRateLimit";

    /** 信封解码（CODEC 阶段锚点） */
    public static final String MESSAGE_DECODER = "gjMessageDecoder";

    /** 信封编码 */
    public static final String MESSAGE_ENCODER = "gjMessageEncoder";

    /** topic 派发（DISPATCH 阶段锚点） */
    public static final String TOPIC_DISPATCH = "gjTopicDispatch";

    /** 兜底异常 */
    public static final String EXCEPTION_TRACKER = "gjExceptionTracker";

    private HandlerNames() {
    }
}
