package com.gj.llm.mq.adapter.rocket;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.mq.constant.MqConstants;
import com.gj.llm.mq.model.GjMessage;
import com.gj.llm.mq.model.SendResult;
import com.gj.llm.mq.spi.MqCapability;
import com.gj.llm.mq.spi.MqProducerBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * RocketMQ 生产端适配 —— 统一信封 → rocketmq-client Message：
 * tag 映射为 RocketMQ tag，扩展头映射为用户属性（userProperty），
 * 负载为信封整体 JSON（UTF-8）。
 *
 * <p>延迟投递：RocketMQ 官方延迟级别为离散档位（1s/5s/10s/30s/1m/.../1h/2h），
 * {@link #nearestDelayLevel(Duration)} 取不小于目标时长的最小档。</p>
 *
 * @author gj-llm
 */
@Slf4j
@RequiredArgsConstructor
public class RocketMqProducerBinder implements MqProducerBinder {

    /**
     * RocketMQ 原生延迟级别（秒）：messageDelayLevel 默认 18 档
     */
    static final int[] DELAY_LEVEL_SECONDS = {
            1, 5, 10, 30, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 1200, 1800, 3600, 7200
    };

    /**
     * 业务消息：支持延迟（离散档位）；顺序消费/事务消息 P1 未暴露
     */
    private static final MqCapability CAPABILITY = MqCapability.of(true, false, false);

    private final DefaultMQProducer producer;

    @Override
    public MqCapability capability() {
        return CAPABILITY;
    }

    @Override
    public SendResult send(GjMessage message) {
        SendResult result = doSend(toRocketMessage(message, 0));
        return result.ack() ? SendResult.ok(message.getMessageId()) : result;
    }

    @Override
    public CompletableFuture<SendResult> sendAsync(GjMessage message) {
        Message rocketMessage = toRocketMessage(message, 0);
        CompletableFuture<SendResult> future = new CompletableFuture<>();
        try {
            producer.send(rocketMessage, new SendCallback() {
                @Override
                public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                    future.complete(toResult(message, sendResult));
                }

                @Override
                public void onException(Throwable e) {
                    log.error("[gj-mq] RocketMQ 异步发送失败: topic={}, messageId={}",
                            message.getTopic(), message.getMessageId(), e);
                    future.complete(SendResult.fail(message.getMessageId(), e.getMessage()));
                }
            });
        } catch (Exception e) {
            future.complete(SendResult.fail(message.getMessageId(), e.getMessage()));
        }
        return future;
    }

    @Override
    public void sendDelay(GjMessage message, Duration delay) {
        int level = nearestDelayLevel(delay);
        log.info("[gj-mq] 延迟投递: topic={}, 目标延迟={}ms, 实际档位={}（{}秒）",
                message.getTopic(), delay.toMillis(), level, DELAY_LEVEL_SECONDS[level - 1]);
        doSend(toRocketMessage(message, level));
    }

    // ==================== 内部 ====================

    private Message toRocketMessage(GjMessage message, int delayLevel) {
        byte[] body = JacksonUtils.toJson(message).getBytes(StandardCharsets.UTF_8);
        Message msg = new Message(message.getTopic(),
                message.getTag() == null || message.getTag().isBlank() ? "" : message.getTag(),
                body);
        msg.putUserProperty(MqConstants.HEADER_MESSAGE_ID, message.getMessageId());
        message.getHeaders().forEach(msg::putUserProperty);
        if (delayLevel > 0) {
            msg.setDelayTimeLevel(delayLevel);
        }
        return msg;
    }

    private SendResult doSend(Message rocketMessage) {
        try {
            org.apache.rocketmq.client.producer.SendResult sendResult = producer.send(rocketMessage);
            return toResult(null, sendResult);
        } catch (Exception e) {
            log.error("[gj-mq] RocketMQ 发送失败: topic={}", rocketMessage.getTopic(), e);
            throw new IllegalStateException("[gj-mq] RocketMQ 发送失败: " + e.getMessage(), e);
        }
    }

    private SendResult toResult(GjMessage message, org.apache.rocketmq.client.producer.SendResult sendResult) {
        String messageId = message != null ? message.getMessageId() : sendResult.getMsgId();
        boolean ok = sendResult.getSendStatus() == SendStatus.SEND_OK;
        return ok ? SendResult.ok(messageId) : SendResult.fail(messageId, sendResult.getSendStatus().name());
    }

    /**
     * Duration → 官方延迟档位：取不小于目标时长的最小档（毫秒向上取整，避免 30.001s 误落 30s 档）；
     * 超出最大档（2h）取最大档。返回 1-based 档位号（可直接 setDelayTimeLevel）。
     */
    static int nearestDelayLevel(Duration delay) {
        long millis = Math.max(1, delay.toMillis());
        for (int i = 0; i < DELAY_LEVEL_SECONDS.length; i++) {
            if (millis <= DELAY_LEVEL_SECONDS[i] * 1000L) {
                return i + 1;
            }
        }
        return DELAY_LEVEL_SECONDS.length;
    }
}
