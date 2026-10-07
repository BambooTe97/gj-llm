package com.gj.llm.netty.protocol;

import lombok.Getter;

/**
 * 断开原因码 —— 每次连接关闭必须携带，用于结构化审计与告警。
 * WebSocket 层映射为标准的 Close 状态码（1000 正常 / 1001 离开 / 1008 策略 / 1009 超大 / 1011 服务端错误）。
 *
 * @author gj-llm
 */
@Getter
public enum CloseReason {

    /** 客户端主动断开或 TCP 层断开 */
    CLIENT_CLOSE(1000, "client close"),

    /** 服务端优雅停机 */
    SERVER_SHUTDOWN(1001, "server shutdown"),

    /** 读空闲超时（半开连接清理） */
    IDLE_TIMEOUT(1008, "idle timeout"),

    /** 握手/首帧鉴权失败 */
    AUTH_FAILED(1008, "auth failed"),

    /** 上行限速触发（持续超速） */
    RATE_LIMITED(1008, "rate limited"),

    /** 连接数配额超限（单 IP / 单 principal） */
    QUOTA_EXCEEDED(1008, "quota exceeded"),

    /** 帧长超过上限 */
    FRAME_TOO_LARGE(1009, "frame too large"),

    /** 派发队列溢出，按慢消费者断开 */
    SLOW_CONSUMER(1001, "slow consumer"),

    /** 服务端内部错误 */
    SERVER_ERROR(1011, "server error");

    private final int wsCode;
    private final String phrase;

    CloseReason(int wsCode, String phrase) {
        this.wsCode = wsCode;
        this.phrase = phrase;
    }
}
