package com.gj.llm.redis.config;

import com.gj.llm.redis.serializer.Jackson3JsonRedisSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Redis 配置 -- 定义带类型信息的 {@link RedisTemplate}。
 *
 * <h3>提供的 Bean</h3>
 * <ul>
 *   <li>{@link RedisTemplate}：key 用 {@link StringRedisSerializer}，value 用
 *       {@link Jackson3JsonRedisSerializer}（Jackson 3 + 默认类型），支持任意对象自动往返</li>
 * </ul>
 *
 * <p>{@link org.springframework.data.redis.core.StringRedisTemplate} 由 Spring Boot 自动配置，
 * 无需在此重复定义，{@code RedisService} 直接使用它做基于 JSON 字符串的显式类型读写。</p>
 *
 * <p><b>安全</b>：默认类型序列化器会写入 {@code @class} 类型信息，反序列化时由
 * {@link BasicPolymorphicTypeValidator} 限定允许的包名（{@code com.gj.llm}、{@code java.lang/util/time/math}），
 * 避免反序列化漏洞。</p>
 *
 * @author gj-llm
 */
@Configuration
public class RedisConfig {

    /**
     * 构建带默认类型的 {@link JsonMapper}（仅供 {@link RedisTemplate} 的值序列化器使用）。
     *
     * <p>与全局 {@code JsonMapper}（由 {@code JacksonConfig} 增强，Long->String 供前端使用）相互独立，
     * 此处 mapper 保留原生类型，便于缓存对象自动往返。</p>
     */
    private JsonMapper typedMapper() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType("com.gj.llm.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .build();
        return JsonMapper.builder()
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL_AND_RECORDS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /**
     * 通用对象 RedisTemplate：key/value 均以 JSON（Jackson 3）序列化。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = StringRedisSerializer.UTF_8;
        Jackson3JsonRedisSerializer jsonSerializer = new Jackson3JsonRedisSerializer(typedMapper());

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
