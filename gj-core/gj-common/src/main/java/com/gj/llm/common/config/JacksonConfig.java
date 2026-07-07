package com.gj.llm.common.config;

import com.gj.llm.common.util.JacksonUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Component
public class JacksonConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof JsonMapper jsonMapper) {
            return JacksonUtils.enhanceObjectMapper(jsonMapper)
                    .rebuild()
                    .addModule(new SimpleModule()
                            .addSerializer(Long.class, ToStringSerializer.instance)
                            .addSerializer(Long.TYPE, ToStringSerializer.instance))
                    .build();
        }
        return bean;
    }
}
