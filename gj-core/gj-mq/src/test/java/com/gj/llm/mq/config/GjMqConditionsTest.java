package com.gj.llm.mq.config;

import com.gj.llm.mq.service.GjMqTemplate;
import com.gj.llm.mq.spi.MqProducerBinder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 条件装配单测 —— enabled/type 开关行为（关闭零启动、未选型 fail-fast、选型后适配器就位）。
 */
class GjMqConditionsTest {

    /** 测试装配：@EnableConfigurationProperties 提供 @ConfigurationProperties 绑定后置处理器（纯 runner 无 Boot 自动装配） */
    @Configuration
    @EnableConfigurationProperties(GjMqProperties.class)
    static class TestPropsConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(com.gj.llm.mq.core.MqMetrics.class)
            .withUserConfiguration(TestPropsConfig.class, GjMqConfig.class,
                    com.gj.llm.mq.config.kafka.KafkaMqConfig.class);

    @Test
    void disabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(GjMqTemplate.class);
            assertThat(context).doesNotHaveBean(MqProducerBinder.class);
        });
    }

    @Test
    void enabledWithoutTypeFailsFastOnSend() {
        runner.withPropertyValues("gj.mq.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(GjMqTemplate.class);
                    assertThat(context).doesNotHaveBean(MqProducerBinder.class);
                    GjMqTemplate template = context.getBean(GjMqTemplate.class);
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> template.send("t", null))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("gj.mq.type");
                });
    }

    @Test
    void kafkaTypeAssemblesKafkaBinder() {
        runner.withPropertyValues(
                        "gj.mq.enabled=true",
                        "gj.mq.type=kafka",
                        "gj.mq.kafka.bootstrap-servers=localhost:9092")
                .run(context -> {
                    assertThat(context).hasSingleBean(GjMqTemplate.class);
                    MqProducerBinder binder = context.getBean(MqProducerBinder.class);
                    assertThat(binder).isInstanceOf(
                            com.gj.llm.mq.adapter.kafka.KafkaMqProducerBinder.class);
                    // Kafka 能力声明：不支持延迟
                    org.assertj.core.api.Assertions.assertThat(binder.capability().supportsDelay()).isFalse();
                });
    }

    @Test
    void kafkaTypeWithoutBootstrapFailsWithClearError() {
        runner.withPropertyValues("gj.mq.enabled=true", "gj.mq.type=kafka")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void kafkaCriticalKeysAreProtectedFromPropertiesOverride() {
        // 透传试图覆盖基座锁定的关键配置（序列化器/手动 ack/发送超时），应被忽略并保持内置值
        runner.withPropertyValues(
                        "gj.mq.enabled=true",
                        "gj.mq.type=kafka",
                        "gj.mq.kafka.bootstrap-servers=localhost:9092",
                        "gj.mq.kafka.properties.enable.auto.commit=true",
                        "gj.mq.kafka.properties.value.deserializer=org.apache.kafka.common.serialization.LongDeserializer",
                        "gj.mq.kafka.properties.delivery.timeout.ms=1")
                .run(context -> {
                    Map<String, Object> consumerConfigs = context.getBean(DefaultKafkaConsumerFactory.class)
                            .getConfigurationProperties();
                    assertThat(consumerConfigs.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
                    assertThat(consumerConfigs.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
                            .isEqualTo(StringDeserializer.class);

                    Map<String, Object> producerConfigs = context.getBean(DefaultKafkaProducerFactory.class)
                            .getConfigurationProperties();
                    assertThat(producerConfigs.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG)).isEqualTo(10000);
                    assertThat(producerConfigs.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG)).isEqualTo(10000);
                    assertThat(producerConfigs.get(ProducerConfig.MAX_BLOCK_MS_CONFIG)).isEqualTo(10000);
                });
    }
}
