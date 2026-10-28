package com.gj.llm.mq.config;

import com.gj.llm.mq.core.MqMetrics;
import com.gj.llm.mq.service.GjMqTemplate;
import com.gj.llm.mq.service.impl.GjMqTemplateImpl;
import com.gj.llm.mq.spi.MqProducerBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gj-mq 模块总开关 —— {@code gj.mq.enabled=true} 时装配发送门面。
 *
 * <p>适配器实现不在此装配：各适配器配置类按 {@code gj.mq.type} + {@code @ConditionalOnClass}
 * 双条件自装配（见 {@code config/kafka/KafkaMqConfig}、{@code config/rocket/RocketMqConfig}）。
 * enabled=true 但未选型/缺客户端时，门面仍存在，发送时给出明确错误提示（fail-fast + 可诊断）。</p>
 *
 * @author gj-llm
 */
@Configuration
@ConditionalOnProperty(prefix = "gj.mq", name = "enabled", havingValue = "true")
public class GjMqConfig {

    @Bean
    public GjMqTemplate gjMqTemplate(ObjectProvider<MqProducerBinder> binderProvider, MqMetrics metrics) {
        return new GjMqTemplateImpl(binderProvider, metrics);
    }
}
