package com.gj.llm.mq.core;

import com.gj.llm.mq.annotation.MqListener;
import com.gj.llm.mq.constant.MqConstants;
import com.gj.llm.mq.model.GjMessage;
import lombok.Getter;

import java.lang.reflect.Method;

/**
 * 一个被 {@code @MqListener} 标记的监听方法 —— 注册期完成参数模式分析（消费期零反射决策开销）。
 *
 * @author gj-llm
 */
@Getter
public class MethodMqListener {

    /**
     * 监听方法参数模式
     */
    public enum ParamMode {
        /**
         * 无参
         */
        NONE,
        /**
         * 参数为 GjMessage 完整信封
         */
        ENVELOPE,
        /**
         * 参数为 String，取 payload 原始 JSON
         */
        JSON_STRING,
        /**
         * 参数为业务类型，payload 自动反序列化
         */
        TYPED
    }

    private final Object bean;
    private final Method method;
    private final String topic;
    private final String group;
    private final int concurrency;
    private final String queue;
    private final ParamMode paramMode;
    private final Class<?> payloadType;
    /**
     * 竞争消费分组键（group + topic），适配器据此建容器
     */
    private final String bindingKey;

    private MethodMqListener(Object bean, Method method, MqListener anno, ParamMode paramMode, Class<?> payloadType) {
        this.bean = bean;
        this.method = method;
        this.topic = anno.topic();
        this.group = anno.group();
        this.concurrency = anno.concurrency();
        this.queue = anno.queue();
        this.paramMode = paramMode;
        this.payloadType = payloadType;
        this.bindingKey = group + "|" + topic;
    }

    /**
     * 注册期构建：校验 topic 命名与方法签名（0 或 1 参数）并解析参数模式。
     */
    public static MethodMqListener of(Object bean, Method method, MqListener anno) {
        if (!MqConstants.isValidTopic(anno.topic())) {
            throw new IllegalArgumentException(
                    "@MqListener 非法 topic: " + anno.topic() + "（命名约定: " + MqConstants.TOPIC_PATTERN + "）: "
                            + method.getDeclaringClass().getName() + "#" + method.getName());
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 1) {
            throw new IllegalArgumentException("@MqListener 方法最多 1 个参数: " + method.getDeclaringClass().getName() + "#" + method.getName());
        }
        if (paramTypes.length == 0) {
            return new MethodMqListener(bean, method, anno, ParamMode.NONE, null);
        }
        Class<?> paramType = paramTypes[0];
        if (GjMessage.class == paramType) {
            return new MethodMqListener(bean, method, anno, ParamMode.ENVELOPE, null);
        }
        if (String.class == paramType) {
            return new MethodMqListener(bean, method, anno, ParamMode.JSON_STRING, null);
        }
        return new MethodMqListener(bean, method, anno, ParamMode.TYPED, paramType);
    }
}
