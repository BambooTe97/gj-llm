package com.gj.llm.mq.model;

/**
 * 发送回执 —— P1 语义：消息成功送达 Broker 即 ack（不含消费确认）。
 *
 * <p>Kafka 的 send 本就返回 future；RocketMQ 同步 send 返回 SendStatus；
 * 两者的"投递成功"都收敛为本类型，业务无需感知差异。</p>
 *
 * @author gj-llm
 */
public record SendResult(String messageId, boolean ack, String detail) {

    public static SendResult ok(String messageId) {
        return new SendResult(messageId, true, null);
    }

    public static SendResult fail(String messageId, String detail) {
        return new SendResult(messageId, false, detail);
    }
}
