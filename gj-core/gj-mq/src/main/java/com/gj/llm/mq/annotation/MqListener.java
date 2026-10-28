package com.gj.llm.mq.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为 MQ 消费者 —— 语义为"至少一次"，所有适配器行为统一：
 *
 * <ul>
 *   <li>RocketMQ：异常时返回 RECONSUME_LATER，Broker 按延迟级别重投，
 *       超过 {@code gj.mq.rocket.max-reconsume-times} 自动进死信 topic（%DLQ%消费组）</li>
 *   <li>Kafka：异常时本地退避重试，耗尽后投递到死信 topic（{topic}.dlq）再提交位移</li>
 * </ul>
 *
 * <p>方法签名只允许 0 或 1 个参数：{@code GjMessage}（完整信封），
 * {@code String}（payload 原始 JSON），或任意可反序列化的业务类型（payload 自动转换）。</p>
 *
 * <p>同一个 {@code group + topic} 组合只允许一个监听方法（同组多监听 = 竞争消费，绑定时告警并仅绑首个）。</p>
 *
 * @author gj-llm
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MqListener {

    /** 业务主题（如 notify.message、rag.document.ingest） */
    String topic();

    /**
     * 消费组：RocketMQ 即消费组（决定死信 topic 归属与集群内竞争消费）；
     * Kafka 即 consumer group。同组实例竞争消费（集群横向扩容即分摊）。
     */
    String group() default "default";

    /** 消费并发：-1 = 用适配器默认值（gj.mq.rocket.consume-threads / gj.mq.kafka.concurrency） */
    int concurrency() default -1;

    /** 预留：自定义队列/订阅后缀。P1 未启用，保留占位避免后续加参破坏调用方 */
    String queue() default "";
}
