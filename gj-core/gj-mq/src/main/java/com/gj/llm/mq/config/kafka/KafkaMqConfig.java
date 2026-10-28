package com.gj.llm.mq.config.kafka;

import com.gj.llm.mq.adapter.kafka.KafkaMqConsumerBinder;
import com.gj.llm.mq.adapter.kafka.KafkaMqProducerBinder;
import com.gj.llm.mq.config.GjMqProperties;
import com.gj.llm.mq.core.MqMetrics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 适配器装配 —— 双条件激活：{@code gj.mq.enabled=true} 且 {@code gj.mq.type=kafka}，
 * 且 classpath 存在 spring-kafka（未选型方不声明依赖时整体短路，零影响）。
 *
 * <p>自持 Producer/ConsumerFactory（key/value 固定 String 序列化）：信封是确定性格式的 JSON 字符串，
 * 不与使用方的 {@code spring.kafka.*} 序列化器配置互相干扰——基座通道与业务通道彻底隔离。
 * 团队自有 Kafka 使用场景照常走 Boot 标准配置，互不影响。</p>
 *
 * @author gj-llm
 */
@Configuration
@ConditionalOnProperty(prefix = "gj.mq", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "gj.mq", name = "type", havingValue = "kafka")
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaMqConfig {

    @Bean
    public DefaultKafkaProducerFactory<String, String> gjMqKafkaProducerFactory(GjMqProperties properties) {
        GjMqProperties.Kafka kafka = requireBootstrap(properties);
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 团队调优透传（优先级高于内置默认值）
        configs.putAll(kafka.getProperties());
        return new DefaultKafkaProducerFactory<>(configs);
    }

    @Bean
    public KafkaTemplate<String, String> gjMqKafkaTemplate(
            DefaultKafkaProducerFactory<String, String> gjMqKafkaProducerFactory) {
        return new KafkaTemplate<>(gjMqKafkaProducerFactory);
    }

    @Bean
    public DefaultKafkaConsumerFactory<String, String> gjMqKafkaConsumerFactory(GjMqProperties properties) {
        GjMqProperties.Kafka kafka = requireBootstrap(properties);
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // 手动 ack：处理完成才提交位移（至少一次语义的另一半在消费失败 → 死信逻辑里）
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // 与 RocketMQ CONSUME_FROM_LAST_OFFSET 行为对齐；历史数据回放用 properties 透传 earliest 覆盖
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        configs.putAll(kafka.getProperties());
        return new DefaultKafkaConsumerFactory<>(configs);
    }

    @Bean
    public KafkaMqProducerBinder kafkaMqProducerBinder(KafkaTemplate<String, String> gjMqKafkaTemplate) {
        return new KafkaMqProducerBinder(gjMqKafkaTemplate);
    }

    @Bean
    public KafkaMqConsumerBinder kafkaMqConsumerBinder(
            DefaultKafkaConsumerFactory<String, String> gjMqKafkaConsumerFactory,
            KafkaTemplate<String, String> gjMqKafkaTemplate,
            GjMqProperties properties,
            MqMetrics metrics) {
        return new KafkaMqConsumerBinder(gjMqKafkaConsumerFactory, gjMqKafkaTemplate, properties, metrics);
    }

    // ==================== 内部 ====================

    private GjMqProperties.Kafka requireBootstrap(GjMqProperties properties) {
        GjMqProperties.Kafka kafka = properties.getKafka();
        if (!StringUtils.hasText(kafka.getBootstrapServers())) {
            throw new IllegalStateException("[gj-mq] gj.mq.type=kafka 需配置 gj.mq.kafka.bootstrap-servers");
        }
        return kafka;
    }
}
