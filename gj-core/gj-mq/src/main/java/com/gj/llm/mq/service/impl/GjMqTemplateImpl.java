package com.gj.llm.mq.service.impl;

import com.gj.llm.mq.constant.MqConstants;
import com.gj.llm.mq.core.MqMetrics;
import com.gj.llm.mq.model.GjMessage;
import com.gj.llm.mq.model.SendResult;
import com.gj.llm.mq.service.GjMqTemplate;
import com.gj.llm.mq.spi.MqProducerBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 统一发送门面实现 —— 职责收敛为三件事：信封构建（ID/时间戳）、能力前置校验、指标埋点；
 * 翻译厂商协议是 {@link MqProducerBinder} 实现的事，本类不出现任何厂商 API。
 *
 * @author gj-llm
 */
@Slf4j
@RequiredArgsConstructor
public class GjMqTemplateImpl implements GjMqTemplate {

    private final ObjectProvider<MqProducerBinder> binderProvider;
    private final MqMetrics metrics;

    @Override
    public SendResult send(String topic, Object payload) {
        return send(topic, null, payload);
    }

    @Override
    public SendResult send(String topic, String tag, Object payload) {
        validateTopic(topic);
        MqProducerBinder binder = binder();
        GjMessage message = GjMessage.of(topic, tag, payload);
        long t0 = System.nanoTime();
        try {
            SendResult result = binder.send(message);
            metrics.sendDuration(topic, elapsedMillis(t0));
            metrics.sent(topic, true);
            return result;
        } catch (RuntimeException e) {
            metrics.sendDuration(topic, elapsedMillis(t0));
            metrics.sent(topic, false);
            log.error("[gj-mq] 发送失败: topic={}, tag={}, messageId={}", topic, tag, message.getMessageId(), e);
            throw e;
        }
    }

    @Override
    public CompletableFuture<SendResult> sendAsync(String topic, String tag, Object payload) {
        validateTopic(topic);
        GjMessage message = GjMessage.of(topic, tag, payload);
        long t0 = System.nanoTime();
        return binder().sendAsync(message)
                .whenComplete((result, e) -> {
                    metrics.sendDuration(topic, elapsedMillis(t0));
                    metrics.sent(topic, e == null && result.ack());
                    if (e != null || !result.ack()) {
                        log.error("[gj-mq] 异步发送失败: topic={}, messageId={}", topic, message.getMessageId(), e);
                    }
                });
    }

    @Override
    public void sendDelay(String topic, String tag, Object payload, Duration delay) {
        validateTopic(topic);
        MqProducerBinder binder = binder();
        if (!binder.capability().supportsDelay()) {
            throw new UnsupportedOperationException(
                    "当前 MQ 适配器不支持延迟投递（capability.supportsDelay()=false）: "
                            + binder.getClass().getSimpleName());
        }
        long t0 = System.nanoTime();
        try {
            binder.sendDelay(GjMessage.of(topic, tag, payload), delay);
            metrics.sendDuration(topic, elapsedMillis(t0));
            metrics.sent(topic, true);
        } catch (RuntimeException e) {
            metrics.sendDuration(topic, elapsedMillis(t0));
            metrics.sent(topic, false);
            log.error("[gj-mq] 延迟投递失败: topic={}, tag={}", topic, tag, e);
            throw e;
        }
    }

    // ==================== 内部 ====================

    private MqProducerBinder binder() {
        MqProducerBinder binder = binderProvider.getIfAvailable();
        if (binder == null) {
            throw new IllegalStateException(
                    "[gj-mq] 已启用但无可用适配器：请检查 gj.mq.type 配置与对应客户端依赖"
                            + "（rocketmq → rocketmq-client / kafka → spring-boot-starter-kafka）");
        }
        return binder;
    }

    /** topic 命名防御性校验（RocketMQ 与 Kafka 字符集公共子集），非法值快速失败给出明确诊断 */
    private void validateTopic(String topic) {
        if (!MqConstants.isValidTopic(topic)) {
            throw new IllegalArgumentException(
                    "[gj-mq] 非法 topic: " + topic + "（命名约定: " + MqConstants.TOPIC_PATTERN + "）");
        }
    }

    private long elapsedMillis(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }
}
