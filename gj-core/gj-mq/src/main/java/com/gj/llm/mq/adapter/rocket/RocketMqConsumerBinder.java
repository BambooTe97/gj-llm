package com.gj.llm.mq.adapter.rocket;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.mq.config.GjMqProperties;
import com.gj.llm.mq.constant.MqConstants;
import com.gj.llm.mq.core.MethodMqListener;
import com.gj.llm.mq.core.MqListenerInvoker;
import com.gj.llm.mq.core.MqMetrics;
import com.gj.llm.mq.model.GjMessage;
import com.gj.llm.mq.spi.MqConsumerBinder;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.DisposableBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RocketMQ 消费端适配 —— 每个 {@code group + topic} 组合一个 {@link DefaultMQPushConsumer}：
 *
 * <ul>
 *   <li>重试：监听方法抛异常 → 返回 RECONSUME_LATER，Broker 按延迟级别自动重投</li>
 *   <li>死信：重投超过 {@code gj.mq.rocket.max-reconsume-times} → Broker 自动投递 %DLQ%{group}（原生能力，零配置）</li>
 *   <li>集群模式（CLUSTERING）：同组多实例竞争消费，横向扩容即分摊</li>
 * </ul>
 *
 * @author gj-llm
 */
@Slf4j
public class RocketMqConsumerBinder implements MqConsumerBinder, DisposableBean {

    private final GjMqProperties properties;
    private final MqMetrics metrics;
    private final List<DefaultMQPushConsumer> consumers = new java.util.concurrent.CopyOnWriteArrayList<>();

    public RocketMqConsumerBinder(GjMqProperties properties, MqMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void bind(List<MethodMqListener> listeners) {
        // 同 group+topic 多方法 = 竞争消费（消息只到一个方法），告警并仅绑首个
        Map<String, List<MethodMqListener>> grouped = listeners.stream()
                .collect(Collectors.groupingBy(MethodMqListener::getBindingKey,
                        java.util.LinkedHashMap::new, Collectors.toList()));
        grouped.forEach((key, group) -> {
            if (group.size() > 1) {
                log.warn("[gj-mq] bindingKey={} 存在 {} 个 @MqListener 方法（竞争消费），仅绑定第一个: {}#{}",
                        key, group.size(), group.get(0).getBean().getClass().getSimpleName(),
                        group.get(0).getMethod().getName());
            }
            startConsumer(group.get(0));
        });
    }

    // ==================== 内部 ====================

    private void startConsumer(MethodMqListener listener) {
        GjMqProperties.Rocket rocket = properties.getRocket();
        int concurrency = listener.getConcurrency() > 0 ? listener.getConcurrency() : rocket.getConsumeThreads();
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(listener.getGroup());
        consumer.setNamesrvAddr(rocket.getNameServer());
        try {
            consumer.subscribe(listener.getTopic(), "*");
        } catch (Exception e) {
            throw new IllegalStateException("[gj-mq] RocketMQ 订阅失败: topic=" + listener.getTopic(), e);
        }
        consumer.setConsumeThreadMin(concurrency);
        consumer.setConsumeThreadMax(concurrency);
        // 单批 1 条：失败时只重投当前消息，避免整批连坐
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setMaxReconsumeTimes(rocket.getMaxReconsumeTimes());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.registerMessageListener(
                (org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently)
                        (msgs, context) -> consume(listener, msgs));
        try {
            consumer.start();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[gj-mq] RocketMQ 消费者启动失败: topic=" + listener.getTopic()
                            + ", group=" + listener.getGroup(), e);
        }
        consumers.add(consumer);
        log.info("[gj-mq] RocketMQ 消费容器启动: topic={}, group={}, threads={}",
                listener.getTopic(), listener.getGroup(), concurrency);
    }

    private org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus consume(
            MethodMqListener listener, List<MessageExt> msgs) {
        for (MessageExt msg : msgs) {
            GjMessage message;
            try {
                message = JacksonUtils.fromJson(new String(msg.getBody(), StandardCharsets.UTF_8), GjMessage.class);
            } catch (Exception e) {
                // 坏消息（非本模块信封格式）：重投永远不会有结果，记 ERROR 后跳过（RECONSUME_SUCCESS 放行）
                log.error("[gj-mq] 消息反序列化失败，跳过（建议检查生产方是否为 gj-mq 信封）: topic={}, msgId={}",
                        listener.getTopic(), msg.getMsgId(), e);
                metrics.consumed(listener.getTopic(), false);
                continue;
            }
            message.setRetryCount(msg.getReconsumeTimes());
            long t0 = System.nanoTime();
            try {
                MqListenerInvoker.invoke(listener, message);
                metrics.consumeDuration(listener.getTopic(), elapsedMillis(t0));
                metrics.consumed(listener.getTopic(), true);
            } catch (Exception e) {
                metrics.consumeDuration(listener.getTopic(), elapsedMillis(t0));
                metrics.consumed(listener.getTopic(), false);
                log.warn("[gj-mq] 消费失败，等待 Broker 重投（第 {} 次）: topic={}, messageId={}, group={}",
                        msg.getReconsumeTimes() + 1, listener.getTopic(), message.getMessageId(),
                        listener.getGroup(), e);
                return org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private long elapsedMillis(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }

    @Override
    public void destroy() {
        consumers.forEach(DefaultMQPushConsumer::shutdown);
        log.info("[gj-mq] RocketMQ 消费容器已关闭: {} 个", consumers.size());
    }
}
