package com.gj.llm.mq.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * gj-mq 配置属性 —— 前缀 {@code gj.mq}，默认值与 {@code GJ_MQ_GUIDE.md} 配置节一致。
 *
 * <p><b>enabled 默认 false</b>：基座能力按需激活，关闭时模块零启动（不建连接、不起容器）。
 * 使用基座的团队按自有基建选型 {@code gj.mq.type}（rocketmq | kafka），同一套业务代码零改动切换。</p>
 *
 * @author gj-llm
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gj.mq")
public class GjMqProperties {

    /**
     * 模块开关，默认关闭
     */
    private boolean enabled = false;

    /**
     * 适配器选型：none=未选型 | rocketmq | kafka
     */
    private MqType type = MqType.NONE;

    /**
     * RocketMQ 适配器行为面（gj.mq.type=rocketmq 时生效）
     */
    private final Rocket rocket = new Rocket();

    /**
     * Kafka 适配器行为面（gj.mq.type=kafka 时生效）
     */
    private final Kafka kafka = new Kafka();

    public enum MqType {
        NONE, ROCKETMQ, KAFKA
    }

    /**
     * RocketMQ 适配器配置 —— 裸客户端（rocketmq-client 5.x remoting，兼容 NameServer+Broker 经典部署），
     * Boot 4 无官方 starter，连接入口由此配置；重试队列与 {@code %DLQ%消费组} 死信为 Broker 原生行为。
     */
    @Getter
    @Setter
    public static class Rocket {
        /**
         * NameServer 地址（多个用分号分隔），type=rocketmq 时必填
         */
        private String nameServer = "";
        /**
         * 生产者组
         */
        private String producerGroup = "gj_mq_producer";
        /**
         * 同步发送超时（毫秒）
         */
        private int sendTimeoutMillis = 3000;
        /**
         * 消费端最大重试次数，超过后 Broker 自动投递到死信 topic（%DLQ%{consumerGroup}）
         */
        private int maxReconsumeTimes = 3;
        /**
         * 每个消费者并发消费线程数
         */
        private int consumeThreads = 8;
        /** 延迟投递精度：Duration 映射到官方延迟级别（1s/5s/10s/30s/1m/2m/.../30m/1h/2h）取不小于它的最小档 */
        // 说明性注释，无需配置
    }

    /**
     * Kafka 适配器配置 —— 自持 Producer/ConsumerFactory（key/value 固定 String 序列化，信封为确定性格式的 JSON），
     * 不依赖 spring.kafka.* 的序列化器配置，避免团队配置漂移破坏信封格式。
     */
    @Getter
    @Setter
    public static class Kafka {
        /**
         * Broker 连接地址（多个用逗号分隔），type=kafka 时必填
         */
        private String bootstrapServers = "";
        /**
         * 消费失败重试耗尽后的死信 topic 后缀（Kafka 无原生 DLQ，投递到 {topic}{suffix} 后提交位移）
         */
        private String dlqSuffix = ".dlq";
        /**
         * 默认消费并发（@MqListener 未显式指定时生效；建议不超过目标 topic 分区数）
         */
        private int concurrency = 1;
        /**
         * 消费失败本地重试次数（含首次），耗尽后投递死信 topic
         */
        private int maxAttempts = 3;
        /**
         * 重试退避基础间隔（毫秒）
         */
        private long retryBackoffMillis = 1000;
        /**
         * Kafka 同步发送超时（毫秒）—— 内部映射 request.timeout.ms / delivery.timeout.ms / max.block.ms
         * 三件套联动（Kafka 约束 delivery.timeout.ms >= linger.ms + request.timeout.ms，不能单独配置）。
         * properties 透传不得覆盖本项。
         */
        private int sendTimeoutMillis = 10000;
        /**
         * 死信写入失败后的后台重试间隔（毫秒）—— 消费容器暂停期间定时重试写 DLQ，写入成功自动恢复消费
         */
        private long dlqRetryIntervalMillis = 10000;
        /**
         * 追加透传的客户端属性（producer/consumer 共用），如 max.poll.records；优先级高于内置默认值（序列化器/手动 ack/超时等关键配置除外）
         */
        private Map<String, String> properties = new LinkedHashMap<>();
    }
}
