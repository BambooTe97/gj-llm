package com.gj.llm.mq.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * gj-mq 指标绑定 —— 可观测性一等公民：
 *
 * <ul>
 *   <li>{@code gj.mq.sent{topic,result}} —— 发送计数</li>
 *   <li>{@code gj.mq.consumed{topic,result}} —— 消费计数（含重试失败）</li>
 *   <li>{@code gj.mq.send.duration{topic} / gj.mq.consume.duration{topic}} —— 收发耗时</li>
 *   <li>{@code gj.mq.dlq.sent{topic} / gj.mq.dlq.failed{topic}} —— 死信写入成功/失败计数（Kafka 适配器兜底）</li>
 * </ul>
 *
 * <p>topic tag 使用稳定业务主题（低基数）；无 {@link MeterRegistry} bean（未引入 actuator）时
 * 自动降级本地 {@link SimpleMeterRegistry}（gj-netty NettyMetrics 同款）。</p>
 *
 * @author gj-llm
 */
@Component
@ConditionalOnProperty(prefix = "gj.mq", name = "enabled", havingValue = "true")
public class MqMetrics {

    private final MeterRegistry registry;

    public MqMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        MeterRegistry available = registryProvider.getIfAvailable();
        this.registry = available == null ? new SimpleMeterRegistry() : available;
    }

    // ==================== 计数器 ====================

    public void sent(String topic, boolean ok) {
        registry.counter("gj.mq.sent", "topic", topic, "result", ok ? "ok" : "error").increment();
    }

    public void consumed(String topic, boolean ok) {
        registry.counter("gj.mq.consumed", "topic", topic, "result", ok ? "ok" : "error").increment();
    }

    // ==================== 耗时 ====================

    public void sendDuration(String topic, long millis) {
        registry.timer("gj.mq.send.duration", "topic", topic).record(Duration.ofMillis(millis));
    }

    public void consumeDuration(String topic, long millis) {
        registry.timer("gj.mq.consume.duration", "topic", topic).record(Duration.ofMillis(millis));
    }

    // ==================== 死信（Kafka 适配器） ====================

    /**
     * 死信写入成功计数（topic tag 用原始业务主题，低基数）
     */
    public void dlqSent(String topic) {
        registry.counter("gj.mq.dlq.sent", "topic", topic).increment();
    }

    /**
     * 死信写入失败计数（累计失败尝试；持续增长意味着消费容器处于暂停自愈状态，需运维介入）
     */
    public void dlqFailed(String topic) {
        registry.counter("gj.mq.dlq.failed", "topic", topic).increment();
    }
}
