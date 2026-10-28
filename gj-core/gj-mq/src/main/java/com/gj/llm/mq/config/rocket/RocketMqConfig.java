package com.gj.llm.mq.config.rocket;

import com.gj.llm.mq.adapter.rocket.RocketMqConsumerBinder;
import com.gj.llm.mq.adapter.rocket.RocketMqProducerBinder;
import com.gj.llm.mq.config.GjMqProperties;
import com.gj.llm.mq.core.MqMetrics;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * RocketMQ 适配器装配 —— 双条件激活：{@code gj.mq.enabled=true} 且 {@code gj.mq.type=rocketmq}，
 * 且 classpath 存在 rocketmq-client（未选型方不声明该依赖时整体短路，零影响）。
 *
 * <p>裸客户端（Boot 4 无官方 starter）：Producer 在此启动、随容器销毁关闭；
 * 消费者由 {@code RocketMqConsumerBinder} 在监听器注册后创建。</p>
 *
 * @author gj-llm
 */
@Configuration
@ConditionalOnProperty(prefix = "gj.mq", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "gj.mq", name = "type", havingValue = "rocketmq")
@ConditionalOnClass(DefaultMQProducer.class)
public class RocketMqConfig {

    /**
     * 生产者：随应用启动（rocketmq-client 客户端实例轻量，连接在首次发送时建立）。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public DefaultMQProducer gjMqRocketProducer(GjMqProperties properties) {
        GjMqProperties.Rocket rocket = properties.getRocket();
        if (!StringUtils.hasText(rocket.getNameServer())) {
            throw new IllegalStateException("[gj-mq] gj.mq.type=rocketmq 需配置 gj.mq.rocket.name-server");
        }
        DefaultMQProducer producer = new DefaultMQProducer(rocket.getProducerGroup());
        producer.setNamesrvAddr(rocket.getNameServer());
        producer.setSendMsgTimeout(rocket.getSendTimeoutMillis());
        try {
            producer.start();
        } catch (Exception e) {
            throw new IllegalStateException("[gj-mq] RocketMQ Producer 启动失败: " + e.getMessage(), e);
        }
        return producer;
    }

    @Bean
    public RocketMqProducerBinder rocketMqProducerBinder(DefaultMQProducer producer) {
        return new RocketMqProducerBinder(producer);
    }

    @Bean
    public RocketMqConsumerBinder rocketMqConsumerBinder(GjMqProperties properties, MqMetrics metrics) {
        return new RocketMqConsumerBinder(properties, metrics);
    }
}
