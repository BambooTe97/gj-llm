package com.gj.llm.mq.adapter.kafka;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.mq.config.GjMqProperties;
import com.gj.llm.mq.core.MethodMqListener;
import com.gj.llm.mq.core.MqListenerInvoker;
import com.gj.llm.mq.core.MqMetrics;
import com.gj.llm.mq.model.GjMessage;
import com.gj.llm.mq.spi.MqConsumerBinder;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.Acknowledgment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Kafka 消费端适配 —— 每个 {@code group + topic} 组合一个 {@link ConcurrentMessageListenerContainer}：
 *
 * <ul>
 *   <li>ack：手动确认（MANUAL_IMMEDIATE），处理完成才提交位移 —— 至少一次语义</li>
 *   <li>重试：失败本地退避重试 {@code gj.mq.kafka.max-attempts} 次（阻塞当前消费线程）</li>
 *   <li>死信：Kafka 无原生 DLQ —— 耗尽后投递到 {@code {topic}.dlq} topic 再提交位移（跳过毒消息）</li>
 *   <li>死信兜底（企业级）：死信写入失败（如 dlq topic 未创建/broker 异常）内联限次重试后仍失败
 *       → 暂停本消费容器 + 后台定时重试写 DLQ，写入成功自动提交位移并恢复消费（自愈）。
 *       期间不提交位移，应用重启后消息重新投递 —— 不丢消息，只暂停该流</li>
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
    /**
     * 死信重试线程（daemon）：消费容器暂停期间定时重试写 DLQ，随应用关闭
     */
    private final ScheduledExecutorService dlqRetryExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gj-mq-dlq-retry");
                t.setDaemon(true);
                return t;
            });

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
        warnRetryBackoffVsPollInterval();
        // 同 group+topic 多方法 = 竞争消费（消息只到一个方法），告警并仅绑首个
        Map<String, List<MethodMqListener>> grouped = listeners.stream()
                .collect(Collectors.groupingBy(MethodMqListener::getBindingKey,
                        java.util.LinkedHashMap::new, Collectors.toList()));
        grouped.forEach((key, group) -> {
            if (group.size() > 1) {
                log.warn("[gj-mq] bindingKey={} 存在 {} 个 @MqListener 方法（竞争消费），仅绑定第一个: {}#{}",
                        key, group.size(), group.getFirst().getBean().getClass().getSimpleName(),
                        group.getFirst().getMethod().getName());
            }
            startContainer(group.getFirst());
        });
    }

    // ==================== 内部 ====================

    private void startContainer(MethodMqListener listener) {
        GjMqProperties.Kafka kafka = properties.getKafka();
        int concurrency = listener.getConcurrency() > 0 ? listener.getConcurrency() : kafka.getConcurrency();

        ContainerProperties containerProps = new ContainerProperties(listener.getTopic());
        containerProps.setGroupId(listener.getGroup());
        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // topic 暂未创建时不停摆：容器持续探测，topic 建出后自动接管（生产 auto.create 关闭场景）
        containerProps.setMissingTopicsFatal(false);

        // 容器引用经 holder 回传（死信兜底路径在消费期才使用，彼时容器必已创建）
        AtomicReference<ConcurrentMessageListenerContainer<String, String>> containerRef = new AtomicReference<>();
        containerProps.setMessageListener((AcknowledgingMessageListener<String, String>) (record, acknowledgment) -> consume(listener, containerRef.get(), record, acknowledgment));

        ConcurrentMessageListenerContainer<String, String> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, containerProps);
        container.setConcurrency(concurrency);
        containerRef.set(container);
        container.start();
        containers.add(container);
        log.info("[gj-mq] Kafka 消费容器启动: topic={}, group={}, concurrency={}", listener.getTopic(), listener.getGroup(), concurrency);
        log.info("[gj-mq] 死信兜底依赖 {}{} 可写：生产环境（auto.create 关闭）请预建该 topic，否则毒消息会触发本容器暂停自愈",
                listener.getTopic(), kafka.getDlqSuffix());
    }

    private void consume(MethodMqListener listener,
                         ConcurrentMessageListenerContainer<String, String> container,
                         ConsumerRecord<String, String> record,
                         Acknowledgment acknowledgment) {
        String topic = listener.getTopic();
        GjMqProperties.Kafka kafka = properties.getKafka();
        GjMessage message;
        try {
            message = JacksonUtils.fromJson(record.value(), GjMessage.class);
        } catch (Exception e) {
            // 坏消息（非本模块信封格式）：无重试意义，直投死信；死信写失败同样走"暂停自愈"兜底
            log.error("[gj-mq] 消息反序列化失败，投递死信: topic={}, offset={}", topic, record.offset(), e);
            metrics.consumed(topic, false);
            routeToDlq(container, topic, record.value(), acknowledgment, kafka);
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
        log.error("[gj-mq] 消费重试耗尽，投递死信 topic: {}{}（原始 topic={}, messageId={}）",
                topic, kafka.getDlqSuffix(), topic, message.getMessageId());
        metrics.consumeDuration(topic, elapsedMillis(t0));
        routeToDlq(container, topic, record.value(), acknowledgment, kafka);
    }

    /**
     * 死信投递 + 失败兜底：内联限次重试（应对瞬时抖动）→ 仍失败则暂停本消费容器（其它 topic/容器不受影响），
     * 后台定时重试写 DLQ，写入成功自动提交位移并恢复消费。期间不提交位移，消息不丢；
     * 应用重启后消息从 Kafka 重新投递，重走整个流程（至少一次语义闭环）。
     */
    private void routeToDlq(ConcurrentMessageListenerContainer<String, String> container,
                            String topic, String payload,
                            Acknowledgment acknowledgment, GjMqProperties.Kafka kafka) {
        String dlqTopic = topic + kafka.getDlqSuffix();
        int inlineAttempts = Math.max(1, kafka.getMaxAttempts());
        for (int attempt = 1; attempt <= inlineAttempts; attempt++) {
            if (trySendToDlq(topic, dlqTopic, payload)) {
                acknowledgment.acknowledge();
                return;
            }
            if (attempt < inlineAttempts) {
                sleep(kafka.getRetryBackoffMillis());
            }
        }
        // 内联重试耗尽：暂停容器 + 后台重试自愈。位移不提交，消息不丢（重启/重平衡后重投）
        log.error("[gj-mq] 死信写入失败（重试 {} 次），暂停本消费容器，后台每 {}ms 重试直至成功。"
                        + "请检查死信 topic {} 是否已创建 / broker 是否可用（恢复后自动继续消费，无需重启）: topic={}",
                inlineAttempts, kafka.getDlqRetryIntervalMillis(), dlqTopic, topic);
        container.pause();
        scheduleDlqRetry(container, topic, dlqTopic, payload, acknowledgment);
    }

    private void scheduleDlqRetry(ConcurrentMessageListenerContainer<String, String> container,
                                  String topic, String dlqTopic, String payload,
                                  Acknowledgment acknowledgment) {
        try {
            dlqRetryExecutor.schedule(() -> {
                if (trySendToDlq(topic, dlqTopic, payload)) {
                    log.info("[gj-mq] 死信写入成功，恢复消费: dlqTopic={}, topic={}", dlqTopic, topic);
                    try {
                        acknowledgment.acknowledge();
                    } catch (Exception e) {
                        // 位移未提交：重启/重平衡后消息重投（至少一次语义），DLQ 消费方需幂等
                        log.error("[gj-mq] 死信已写入但位移提交失败（消息将在重启/重平衡后重投，注意幂等）: topic={}", topic, e);
                    }
                    container.resume();
                } else {
                    scheduleDlqRetry(container, topic, dlqTopic, payload, acknowledgment);
                }
            }, properties.getKafka().getDlqRetryIntervalMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // 应用关闭中：位移未提交，消息随下次启动重投
            log.debug("[gj-mq] 死信重试调度被拒绝（应用关闭中），消息将随重启重投: topic={}", topic);
        }
    }

    /**
     * 单次死信发送尝试；发送超时由 producer 配置（delivery.timeout.ms，见 KafkaMqConfig）约束
     */
    private boolean trySendToDlq(String topic, String dlqTopic, String payload) {
        try {
            kafkaTemplate.send(dlqTopic, payload).get();
            metrics.dlqSent(topic);
            return true;
        } catch (Exception e) {
            metrics.dlqFailed(topic);
            log.error("[gj-mq] 死信写入失败: dlqTopic={}, topic={}", dlqTopic, topic, e);
            return false;
        }
    }

    /**
     * 启动期校验：本地退避总时长相对 max.poll.interval.ms（默认 5min），阻塞 poll 线程过久会触发 rebalance
     */
    private void warnRetryBackoffVsPollInterval() {
        GjMqProperties.Kafka kafka = properties.getKafka();
        long totalBackoff = (long) (Math.max(1, kafka.getMaxAttempts()) - 1) * kafka.getRetryBackoffMillis();
        long pollInterval = 300_000L;
        String configured = kafka.getProperties().get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG);
        if (configured != null) {
            try {
                pollInterval = Long.parseLong(configured);
            } catch (NumberFormatException e) {
                log.warn("[gj-mq] properties 中 max.poll.interval.ms 非法: {}", configured);
            }
        }
        if (totalBackoff >= pollInterval / 2) {
            log.warn("[gj-mq] 消费重试总退避 {}ms 已接近 max.poll.interval.ms {}ms，存在 rebalance 风险："
                            + "请调小 max-attempts/retry-backoff-millis 或调大 max.poll.interval.ms",
                    totalBackoff, pollInterval);
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
        dlqRetryExecutor.shutdownNow();
        containers.forEach(ConcurrentMessageListenerContainer::stop);
        log.info("[gj-mq] Kafka 消费容器已关闭: {} 个", containers.size());
    }
}
