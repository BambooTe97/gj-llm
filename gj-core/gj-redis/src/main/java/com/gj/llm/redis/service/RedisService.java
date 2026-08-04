package com.gj.llm.redis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 服务封装 -- 基于 {@link StringRedisTemplate} + Jackson 3 提供显式类型的缓存读写。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>使用 {@link StringRedisTemplate} 存取 JSON 字符串，读时由调用方显式指定目标类型
 *       （{@code get(key, Class)} / {@code get(key, TypeReference)}），避免依赖带 {@code @class} 的默认类型</li>
 *   <li>内部 {@link ObjectMapper} 为独立的纯净 mapper（不启用前端用的 Long->String），
 *       保证 Long、日期等原生类型在缓存中正确往返</li>
 *   <li>提供 SCAN 方式的 {@link #deleteByPattern(String)}，避免 {@code KEYS} 阻塞 Redis</li>
 * </ul>
 *
 * @author gj-llm
 */
@Slf4j
@Component
public class RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 独立的纯净 ObjectMapper（无前端 Long->String 转换），保证缓存对象原生类型往返 */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public RedisService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== 写入 ====================

    /**
     * 写入缓存（无过期）。
     */
    public void set(String key, Object value) {
        if (value == null) {
            delete(key);
            return;
        }
        stringRedisTemplate.opsForValue().set(key, toJson(value));
    }

    /**
     * 写入缓存并设置过期时间。
     */
    public void set(String key, Object value, Duration timeout) {
        if (value == null) {
            delete(key);
            return;
        }
        stringRedisTemplate.opsForValue().set(key, toJson(value), timeout);
    }

    /**
     * 仅当 key 不存在时写入（setnx），返回是否写入成功。
     */
    public boolean setIfAbsent(String key, Object value, Duration timeout) {
        if (value == null) {
            return false;
        }
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, toJson(value), timeout);
        return Boolean.TRUE.equals(ok);
    }

    // ==================== 读取 ====================

    /**
     * 读取并反序列化为指定类型。
     */
    public <T> T get(String key, Class<T> type) {
        String json = stringRedisTemplate.opsForValue().get(key);
        return fromJson(json, type);
    }

    /**
     * 读取并按 {@link TypeReference} 反序列化（用于泛型集合，如 {@code Map<String, Set<String>>}）。
     */
    public <T> T get(String key, TypeReference<T> typeRef) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JacksonException e) {
            log.error("Redis 反序列化失败: key={}, type={}", key, typeRef.getType(), e);
            throw new RuntimeException("Redis 反序列化失败: " + typeRef.getType(), e);
        }
    }

    /**
     * 读取旧值并写入新值。
     */
    public <T> T getAndSet(String key, Object newValue, Class<T> type) {
        String json = stringRedisTemplate.opsForValue().getAndSet(key, toJson(newValue));
        return fromJson(json, type);
    }

    // ==================== 删除 / 过期 ====================

    /**
     * 删除单个 key。
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }

    /**
     * 批量删除。
     */
    public long delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        Long count = stringRedisTemplate.delete(keys);
        return count == null ? 0L : count;
    }

    /**
     * 按通配符删除（SCAN + 批量 DEL，避免 KEYS 阻塞）。
     */
    public long deleteByPattern(String pattern) {
        long count = 0L;
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            List<String> batch = new ArrayList<>(100);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= 100) {
                    count += delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                count += delete(batch);
            }
        }
        return count;
    }

    /**
     * 是否存在。
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 设置过期时间。
     */
    public boolean expire(String key, Duration timeout) {
        return Boolean.TRUE.equals(stringRedisTemplate.expire(key, timeout.toMillis(), TimeUnit.MILLISECONDS));
    }

    /**
     * 获取剩余过期时间（秒）；-1 表示永不过期，-2 表示 key 不存在。
     */
    public long getExpire(String key) {
        Long ttl = stringRedisTemplate.getExpire(key);
        return ttl == null ? -2L : ttl;
    }

    // ==================== 计数 ====================

    public long increment(String key) {
        return increment(key, 1L);
    }

    public long increment(String key, long delta) {
        Long val = stringRedisTemplate.opsForValue().increment(key, delta);
        return val == null ? 0L : val;
    }

    // ==================== 序列化工具 ====================

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new RuntimeException("Redis 序列化失败: " + value.getClass().getName(), e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            log.error("Redis 反序列化失败: type={}", type.getName(), e);
            throw new RuntimeException("Redis 反序列化失败: " + type.getName(), e);
        }
    }
}
