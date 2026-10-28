package com.gj.llm.mq.core;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.mq.model.GjMessage;

/**
 * 监听方法调用器 —— 按注册期解析好的参数模式派发，业务异常原样上抛
 * （重试/死信语义由适配器层实现，本类不吞异常）。
 *
 * @author gj-llm
 */
public final class MqListenerInvoker {

    private MqListenerInvoker() {
    }

    /**
     * 调用监听方法。反射异常统一包装为 IllegalStateException，业务异常原样透传。
     */
    public static void invoke(MethodMqListener listener, GjMessage message) {
        try {
            MethodMqListener.ParamMode mode = listener.getParamMode();
            switch (mode) {
                case NONE -> listener.getMethod().invoke(listener.getBean());
                case ENVELOPE -> listener.getMethod().invoke(listener.getBean(), message);
                case JSON_STRING -> listener.getMethod().invoke(listener.getBean(),
                        JacksonUtils.toJson(message.getPayload()));
                case TYPED -> listener.getMethod().invoke(listener.getBean(),
                        message.payloadAs(listener.getPayloadType()));
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 业务异常原样上抛（保留类型与堆栈），供适配器执行重试/死信语义
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException("监听方法抛出受检异常: " + listener.getMethod(), cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("监听方法不可访问: " + listener.getMethod(), e);
        }
    }
}
