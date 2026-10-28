package com.gj.llm.mq.adapter.kafka;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.mq.config.GjMqProperties;
import com.gj.llm.mq.core.MethodMqListener;
import com.gj.llm.mq.core.MqListenerInvoker;
import com.gj.llm.mq.core.MqMetrics;
import com.gj.llm.mq.model.GjMessage;
import com.gj.llm.mq.spi.MqConsumerBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kafka 消费端适配 —— 每个 {@code group + topic} 组合一个 {@link KafkaMessageListenerContainer}：
 *
 * <ul>
 *   <li>ack：手动确认（MANUAL_IMMEDIATE），处理完成才提交位移 —— 至少一次语义</li>
 *   <li>重试：失败本地退避重试 {@code gj.mq.kafka.max-attempts} 次（阻塞当前消费线程）</li>
 *   <li>死信：Kafka 无原生 DLQ —— 耗尽后投递到 {@code {topic}.dlq} topic 再提交位移（跳过毒消息）</li>
 * </ul>
 *
 * @author gj-llm
 */
@Slf4j
public class KafkaMqConsumerBinder implements MqConsumerBinder, DisposableBean {

    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final GjMqProperties properties;
    private final MqMetrics metrics;
    private final List<ConcurrentMessageListenerContainer<String, String>> containers = new ArrayList<>();

    public KafkaMqConsumerBinder(ConsumerFactory<String, String> consumerFactory,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 GjMqProperties properties,
                                 MqMetrics metrics) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
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
            startContainer(group.get(0));
        });
    }

    // ==================== 内部 ====================

    private void startContainer(MethodMqListener listener) {
        GjMqProperties.Kafka kafka = properties.getKafka();
        int concurrency = listener.getConcurrency() > 0 ? listener.getConcurrency() : kafka.getConcurrency();

        ContainerProperties containerProps = new ContainerProperties(listener.getTopic());
        containerProps.setGroupId(listener.getGroup());
        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProps.setMessageListener(
                (org.springframework.kafka.listener.AcknowledgingMessageListener<String, String>)(
                        record, acknowledgment) -> consume(listener, record, acknowledgment));

        ConcurrentMessageListenerContainer<String, String> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, containerProps);
        container.setConcurrency(concurrency);
        container.start();
        containers.add(container);
        log.info("[gj-mq] Kafka 消费容器启动: topic={}, group={}, concurrency={}",
                listener.getTopic(), listener.getGroup(), concurrency);
    }

    @SuppressWarnings("unchecked")
    private void consume(MethodMqListener listener,
                         org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record,
                         Acknowledgment acknowledgment) {
        String topic = listener.getTopic();
        GjMqProperties.Kafka kafka = properties.getKafka();
        GjMessage message;
        try {
            message = JacksonUtils.fromJson(record.value(), GjMessage.class);
        } catch (Exception e) {
            // 坏消息（非本模块信封格式）：无重试意义，直接投死信 topic 后放行
            log.error("[gj-mq] 消息反序列化失败，投递死信: topic={}, offset={}", topic, record.offset(), e);
            metrics.consumed(topic, false);
            sendToDlq(topic + kafka.getDlqSuffix(), record.value(), e);
            acknowledgment.acknowledge();
            return;
        }
        long t0 = System.nanoTime();
        int maxAttempts = Math.max(1, kafka.getMaxAttempts());
        long backoff = kafka.getRetryBackoffMillis();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                MqListenerInvoker.invoke(listener, message);
                metrics.consumeDuration(topic, elapsedMillis(t0));
                metrics.consumed(topic, true);
                acknowledgment.acknowledge();
                return;
            } catch (Exception e) {
                message.setRetryCount(attempt - 1);
                metrics.consumed(topic, false);
                log.warn("[gj-mq] 消费失败 第{}/{}次: topic={}, messageId={}, offset={}",
                        attempt, maxAttempts, topic, message.getMessageId(), record.offset(), e);
                if (attempt < maxAttempts) {
                    sleep(backoff);
                }
            }
        }
        // 重试耗尽 → 死信 topic → 提交位移（毒消息不再阻塞分区）
        log.error("[gj-mq] 消费重试耗尽，投递死信 topic: {}（原始 topic={}, messageId={}）",
                topic + kafka.getDlqSuffix(), topic, message.getMessageId());
        sendToDlq(topic + kafka.getDlqSuffix(), record.value(), null);
        metrics.consumeDuration(topic, elapsedMillis(t0));
        acknowledgment.acknowledge();
    }

    private void sendToDlq(String dlqTopic, String payload, Exception cause) {
        try {
            kafkaTemplate.send(dlqTopic, payload).get();
        } catch (Exception e) {
            log.error("[gj-mq] 死信投递失败（消息将随重平衡重放）: dlqTopic={}", dlqTopic, e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("[gj-mq] 消费重试等待被中断", e);
        }
    }

    private long elapsedMillis(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }

    @Override
    public void destroy() {
        containers.forEach(ConcurrentMessageListenerContainer::stop);
        log.info("[gj-mq] Kafka 消费容器已关闭: {} 个", containers.size());
    }
}
