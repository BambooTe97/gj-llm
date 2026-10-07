package com.gj.llm.netty.protocol;

import java.util.Map;

/**
 * 协议信封 —— 全部长连接消息的统一外壳，格式与 {@code GJ_NETTY_GUIDE.md} 第五节一致：
 *
 * <pre>{@code
 * { "v": 1, "type": "chat.content", "seq": 42, "ack": false, "payload": {}, "ts": 1765000000000 }
 * }</pre>
 *
 * <p>解码时 {@code payload} 保持为 Jackson 的 {@code JsonNode}（结构由 topic 定义方负责）；
 * 编码时可为任意可序列化对象。信封只承载，不解释。</p>
 *
 * @param type    topic，命名规范见 {@link Topic}
 * @param seq     发送方自增序号（请求/响应关联与顺序校验，基座默认 0）
 * @param ack     是否要求接收方回执
 * @param payload 业务数据
 * @param ts      发送时间戳（毫秒）
 * @author gj-llm
 */
public record MessageEnvelope(int v, String type, long seq, boolean ack, Object payload, long ts) {

    /** 当前协议版本，演进规则见 {@code GJ_NETTY_GUIDE.md} 第十三节 */
    public static final int CURRENT_VERSION = 1;

    public MessageEnvelope {
        // payload 可空；其余字段由 MessageCodec 在解码入口校验
    }

    /**
     * 构造一条当前版本的消息（seq=0、不要求回执）。
     */
    public static MessageEnvelope of(String type, Object payload) {
        return new MessageEnvelope(CURRENT_VERSION, type, 0, false, payload, System.currentTimeMillis());
    }

    /**
     * 服务端心跳 ping（写空闲时发出，客户端回复 {@link Topic#SYS_HEARTBEAT} 同名消息即可）。
     */
    public static MessageEnvelope heartbeat() {
        return of(Topic.SYS_HEARTBEAT, Map.of("t", System.currentTimeMillis()));
    }

    /**
     * 优雅停机通知。
     */
    public static MessageEnvelope serverShutdown() {
        return of(Topic.SYS_SERVER_SHUTDOWN, Map.of("reason", "server_shutdown"));
    }
}
