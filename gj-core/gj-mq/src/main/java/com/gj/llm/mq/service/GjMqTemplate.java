package com.gj.llm.mq.service;

import com.gj.llm.mq.model.SendResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * gj-mq 对外统一发送门面 —— 业务模块只需注入本接口即可收发消息，
 * MQ 实现由 {@code gj.mq.type} 配置决定，切换实现零代码改动。
 *
 * <p>业务代码建议用 {@code ObjectProvider<GjMqTemplate>} 注入（gj-netty PushService 同款）：
 * 模块关闭（gj.mq.enabled=false）时本 bean 不存在，业务可优雅降级。</p>
 *
 * <p>异常约定：无可用适配器 → IllegalStateException；适配器不支持的能力（如 Kafka 延迟投递）
 * → UnsupportedOperationException；发送失败 → 厂商客户端异常原样上抛。</p>
 *
 * @author gj-llm
 */
public interface GjMqTemplate {

    /**
     * 同步发送（无 tag）
     */
    SendResult send(String topic, Object payload);

    /**
     * 同步发送
     */
    SendResult send(String topic, String tag, Object payload);

    /**
     * 异步发送：不阻塞调用线程。注意各适配器"送达确认"精度不同（Kafka 为 Broker ack，
     * RocketMQ 为发送回执），future 完成即代表对应适配器语义下的投递完成。
     */
    CompletableFuture<SendResult> sendAsync(String topic, String tag, Object payload);

    /**
     * 延迟投递。RocketMQ 原生支持（映射官方延迟级别，取不小于 delay 的最小档）；
     * Kafka 不支持（capability 声明），调用将抛 UnsupportedOperationException。
     */
    void sendDelay(String topic, String tag, Object payload, Duration delay);
}
