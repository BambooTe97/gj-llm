package com.gj.llm.mq.adapter.kafka;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.mq.constant.MqConstants;
import com.gj.llm.mq.model.GjMessage;
import com.gj.llm.mq.model.SendResult;
import com.gj.llm.mq.spi.MqCapability;
import com.gj.llm.mq.spi.MqProducerBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka 生产端适配 —— 统一信封 → JSON 字符串（StringSerializer，UTF-8）：
 * tag 与扩展头携带于 record header（Kafka 无 tag/属性概念），负载为信封整体 JSON。
 *
 * <p>sendAsync 即 KafkaTemplate.send 原生 future（Broker ack 后完成）——真异步。</p>
 *
 * @author gj-llm
 */
@Slf4j
@RequiredArgsConstructor
public class KafkaMqProducerBinder implements MqProducerBinder {

    /** Kafka 不支持延迟投递（可另建延迟 topic 方案，P1 不引入） */
    private static final MqCapability CAPABILITY = MqCapability.of(false, false, false);

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public MqCapability capability() {
        return CAPABILITY;
    }

    @Override
    public SendResult send(GjMessage message) {
        try {
            kafkaTemplate.send(toRecord(message)).get();
            return SendResult.ok(message.getMessageId());
        } catch (Exception e) {
            log.error("[gj-mq] Kafka 发送失败: topic={}, messageId={}", message.getTopic(), message.getMessageId(), e);
            throw new IllegalStateException("[gj-mq] Kafka 发送失败: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<SendResult> sendAsync(GjMessage message) {
        return kafkaTemplate.send(toRecord(message))
                .handle((result, e) -> e == null
                        ? SendResult.ok(message.getMessageId())
                        : SendResult.fail(message.getMessageId(), e.getMessage()));
    }

    @Override
    public void sendDelay(GjMessage message, Duration delay) {
        throw new UnsupportedOperationException(
                "Kafka 适配器不支持延迟投递（capability.supportsDelay()=false），"
                        + "可改用 RocketMQ 适配器或自建延迟 topic 方案");
    }

    // ==================== 内部 ====================

    private ProducerRecord<String, String> toRecord(GjMessage message) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(message.getTopic(), JacksonUtils.toJson(message));
        record.headers().add(new RecordHeader(MqConstants.HEADER_MESSAGE_ID,
                message.getMessageId().getBytes(StandardCharsets.UTF_8)));
        if (message.getTag() != null && !message.getTag().isBlank()) {
            record.headers().add(new RecordHeader(MqConstants.HEADER_TAG,
                    message.getTag().getBytes(StandardCharsets.UTF_8)));
        }
        message.getHeaders().forEach((name, value) -> record.headers().add(new RecordHeader(name,
                value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8))));
        return record;
    }
}
