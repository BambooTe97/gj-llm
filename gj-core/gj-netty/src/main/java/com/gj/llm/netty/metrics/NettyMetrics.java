package com.gj.llm.netty.metrics;

import com.gj.llm.netty.protocol.CloseReason;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基座指标绑定 —— 可观测性一等公民（{@code GJ_NETTY_GUIDE.md} 第九节）：
 *
 * <ul>
 *   <li>{@code gj.netty.connections.active / opened / closed{reason}}</li>
 *   <li>{@code gj.netty.messages.received{topic} / sent}</li>
 *   <li>{@code gj.netty.messages.rejected{cause}} —— 拒绝数：稳的系统不是不拒绝，而是拒绝时看得见</li>
 *   <li>{@code gj.netty.auth.failures}、{@code gj.netty.exceptions{type}}</li>
 *   <li>{@code gj.netty.slow.consumers}、{@code gj.netty.dispatch.queue.size}</li>
 * </ul>
 *
 * <p>无 {@link MeterRegistry} bean（未引入 actuator）时自动降级本地
 * {@link SimpleMeterRegistry}（无人抓取，仅轻微内存占用）。
 * 仪表经 {@link ObjectProvider} 惰性取被依赖 bean，避免与 SessionRegistry 的构造环。</p>
 *
 * @author gj-llm
 */
@Component
@ConditionalOnProperty(prefix = "gj.netty", name = "enabled", havingValue = "true")
public class NettyMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger slowConsumers = new AtomicInteger();

    /**
     * Micrometer 对 gauge 状态对象持弱引用——Supplier 必须自持强引用，否则被 GC 后仪表恒为 NaN。
     */
    @SuppressWarnings("unused")
    private final List<Object> gaugeStrongRefs;

    public NettyMetrics(ObjectProvider<MeterRegistry> registryProvider,
                        ObjectProvider<com.gj.llm.netty.session.SessionRegistry> sessionRegistryProvider,
                        ObjectProvider<com.gj.llm.netty.dispatch.DispatchService> dispatchServiceProvider) {
        MeterRegistry available = registryProvider.getIfAvailable();
        this.registry = available == null ? new SimpleMeterRegistry() : available;
        java.util.function.Supplier<Number> activeGauge =
                () -> sessionRegistryProvider.getObject().activeCount();
        java.util.function.Supplier<Number> queueGauge =
                () -> dispatchServiceProvider.getObject().inFlightCount();
        this.gaugeStrongRefs = List.of(activeGauge, queueGauge);
        Gauge.builder("gj.netty.connections.active", activeGauge).register(registry);
        Gauge.builder("gj.netty.dispatch.queue.size", queueGauge).register(registry);
        Gauge.builder("gj.netty.slow.consumers", slowConsumers, AtomicInteger::doubleValue)
                .strongReference(true)
                .register(registry);
    }

    // ==================== 计数器 ====================

    public void connectionOpened() {
        registry.counter("gj.netty.connections.opened").increment();
    }

    public void connectionClosed(CloseReason reason) {
        registry.counter("gj.netty.connections.closed", "reason", reason.name()).increment();
    }

    /**
     * 上行消息量。注意 topic tag 基数：若业务 topic 携带会话级后缀（如 chat.123），
     * 基数会随会话数增长——接入方应使用稳定 topic 或由 P2 引入基数控制。
     */
    public void messageReceived(String topic) {
        registry.counter("gj.netty.messages.received", "topic", topic).increment();
    }

    public void messageSent() {
        registry.counter("gj.netty.messages.sent").increment();
    }

    /** 拒绝计数：bad_format / unknown_topic / rate_limit / slow_consumer / quota / offline / ... */
    public void rejected(String cause) {
        registry.counter("gj.netty.messages.rejected", "cause", cause).increment();
    }

    public void authFailure() {
        registry.counter("gj.netty.auth.failures").increment();
    }

    public void exception(Throwable t) {
        registry.counter("gj.netty.exceptions", "type", t.getClass().getSimpleName()).increment();
    }

    public void outboundOversize() {
        registry.counter("gj.netty.messages.outboundOversize").increment();
    }

    // ==================== 慢消费者（写水位超限） ====================

    public void slowConsumerEnter() {
        slowConsumers.incrementAndGet();
    }

    public void slowConsumerLeave() {
        slowConsumers.decrementAndGet();
    }
}
