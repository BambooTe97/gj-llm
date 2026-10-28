package com.gj.llm.mq.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * gj.mq.* 配置绑定单测。
 */
class GjMqPropertiesTest {

    @Test
    void defaultsAreDisabledAndUntyped() {
        GjMqProperties properties = bind(Map.of());

        assertFalse(properties.isEnabled());
        assertEquals(GjMqProperties.MqType.NONE, properties.getType());
        assertEquals("gj_mq_producer", properties.getRocket().getProducerGroup());
        assertEquals(3, properties.getRocket().getMaxReconsumeTimes());
        assertEquals(".dlq", properties.getKafka().getDlqSuffix());
        assertEquals(10000, properties.getKafka().getSendTimeoutMillis());
        assertEquals(10000L, properties.getKafka().getDlqRetryIntervalMillis());
        assertFalse(properties.getRocket().getNameServer() != null
                && !properties.getRocket().getNameServer().isBlank());
    }

    @Test
    void bindsRocketAndKafkaSections() {
        GjMqProperties properties = bind(Map.of(
                "enabled", "true",
                "type", "rocketmq",
                "rocket.name-server", "10.0.0.1:9876;10.0.0.2:9876",
                "rocket.consume-threads", "16",
                "kafka.dlq-suffix", ".dead",
                "kafka.properties.max.poll.records", "200"));

        assertTrue(properties.isEnabled());
        assertEquals(GjMqProperties.MqType.ROCKETMQ, properties.getType());
        assertEquals("10.0.0.1:9876;10.0.0.2:9876", properties.getRocket().getNameServer());
        assertEquals(16, properties.getRocket().getConsumeThreads());
        assertEquals(".dead", properties.getKafka().getDlqSuffix());
        assertEquals("200", properties.getKafka().getProperties().get("max.poll.records"));
    }

    private GjMqProperties bind(Map<String, String> kv) {
        MockEnvironment environment = new MockEnvironment();
        kv.forEach((k, v) -> environment.setProperty("gj.mq." + k, v));
        return Binder.get(environment)
                .bind("gj.mq", Bindable.of(GjMqProperties.class))
                .orElseGet(GjMqProperties::new);
    }
}
