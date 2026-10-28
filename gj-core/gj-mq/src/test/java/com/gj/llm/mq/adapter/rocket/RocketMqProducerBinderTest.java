package com.gj.llm.mq.adapter.rocket;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RocketMQ 延迟档位映射单测 —— Duration → 官方延迟级别（1s/5s/.../2h），取不小于目标的最小档。
 */
class RocketMqProducerBinderTest {

    @Test
    void mapsToNearestCeilingLevel() {
        assertEquals(1, RocketMqProducerBinder.nearestDelayLevel(Duration.ofMillis(100)));
        assertEquals(1, RocketMqProducerBinder.nearestDelayLevel(Duration.ofSeconds(1)));
        assertEquals(2, RocketMqProducerBinder.nearestDelayLevel(Duration.ofSeconds(2)));
        assertEquals(2, RocketMqProducerBinder.nearestDelayLevel(Duration.ofSeconds(5)));
        assertEquals(5, RocketMqProducerBinder.nearestDelayLevel(Duration.ofSeconds(30).plusMillis(1)));
        assertEquals(5, RocketMqProducerBinder.nearestDelayLevel(Duration.ofMinutes(1)));
        assertEquals(17, RocketMqProducerBinder.nearestDelayLevel(Duration.ofHours(1)));
        assertEquals(18, RocketMqProducerBinder.nearestDelayLevel(Duration.ofHours(1).plusSeconds(1)));
    }

    @Test
    void capsAtMaxLevel() {
        assertEquals(18, RocketMqProducerBinder.nearestDelayLevel(Duration.ofHours(24)));
        assertEquals(18, RocketMqProducerBinder.nearestDelayLevel(Duration.ofDays(7)));
    }
}
