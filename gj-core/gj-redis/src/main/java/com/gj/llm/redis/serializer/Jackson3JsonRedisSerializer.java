package com.gj.llm.redis.serializer;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 Jackson 3（{@code tools.jackson}）的 Redis 值序列化器。
 *
 * <p>项目统一使用 Jackson 3，而 Spring Data Redis 自带的是 Jackson 2 系列序列化器，
 * 故此处自实现一个委托给 {@link ObjectMapper} 的序列化器，供 {@code RedisTemplate<String, Object>} 使用。</p>
 *
 * <p>构造时传入的 {@link ObjectMapper} 通常已启用默认类型（{@code activateDefaultTyping}），
 * 序列化时会写入 {@code @class} 类型信息，从而支持任意对象的自动往返（round-trip）。
 * 类型校验器应限定允许的反序列化包名（如 {@code com.gj.llm}、{@code java.util}），避免反序列化漏洞。</p>
 *
 * @author gj-llm
 */
public class Jackson3JsonRedisSerializer implements RedisSerializer<Object> {

    private final ObjectMapper objectMapper;

    public Jackson3JsonRedisSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(Object t) throws SerializationException {
        if (t == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(t);
        } catch (JacksonException e) {
            throw new SerializationException("Jackson 3 序列化失败: " + t.getClass().getName(), e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(bytes, Object.class);
        } catch (JacksonException e) {
            throw new SerializationException("Jackson 3 反序列化失败", e);
        }
    }
}
