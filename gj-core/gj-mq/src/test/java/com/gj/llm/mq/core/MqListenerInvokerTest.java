package com.gj.llm.mq.core;

import com.gj.llm.mq.TestSupport;
import com.gj.llm.mq.annotation.MqListener;
import com.gj.llm.mq.model.GjMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 监听方法注册与调用派发单测 —— 参数模式解析、业务异常透传。
 */
class MqListenerInvokerTest {

    /** 简单业务负载 */
    record DemoPayload(String name) {
    }

    /** 各参数形态的监听样例 */
    static class SampleListener {
        GjMessage lastEnvelope;
        String lastJson;
        DemoPayload lastTyped;
        int noArgCalls;
        boolean boom;

        @MqListener(topic = "t.none")
        public void noArg() {
            noArgCalls++;
        }

        @MqListener(topic = "t.envelope")
        public void envelope(GjMessage message) {
            this.lastEnvelope = message;
        }

        @MqListener(topic = "t.json")
        public void json(String payloadJson) {
            this.lastJson = payloadJson;
        }

        @MqListener(topic = "t.typed")
        public void typed(DemoPayload payload) {
            this.lastTyped = payload;
        }

        @MqListener(topic = "t.boom")
        public void boom(GjMessage message) {
            throw new IllegalStateException("业务异常原样透传");
        }

        @MqListener(topic = "t.bad")
        public void tooManyParams(GjMessage message, String extra) {
            // 注册期应拒绝
        }
    }

    @BeforeAll
    static void initJson() {
        TestSupport.initJackson();
    }

    @Test
    void paramModesResolveAndDispatch() {
        SampleListener sample = new SampleListener();
        SampleListener proxyTarget = sample;

        MethodMqListener none = methodOf(proxyTarget, "noArg");
        MethodMqListener envelope = methodOf(proxyTarget, "envelope");
        MethodMqListener json = methodOf(proxyTarget, "json");
        MethodMqListener typed = methodOf(proxyTarget, "typed");

        assertEquals(MethodMqListener.ParamMode.NONE, none.getParamMode());
        assertEquals(MethodMqListener.ParamMode.ENVELOPE, envelope.getParamMode());
        assertEquals(MethodMqListener.ParamMode.JSON_STRING, json.getParamMode());
        assertEquals(MethodMqListener.ParamMode.TYPED, typed.getParamMode());
        assertEquals(DemoPayload.class, typed.getPayloadType());

        GjMessage message = GjMessage.of("t.typed", "tag", Map.of("name", "文档C"));
        message.setRetryCount(2);

        MqListenerInvoker.invoke(none, message);
        MqListenerInvoker.invoke(envelope, message);
        MqListenerInvoker.invoke(json, message);
        MqListenerInvoker.invoke(typed, message);

        assertEquals(1, sample.noArgCalls);
        assertEquals(message.getMessageId(), sample.lastEnvelope.getMessageId());
        assertEquals(2, sample.lastEnvelope.getRetryCount());
        assertInstanceOf(String.class, sample.lastJson);
        assertEquals("文档C", sample.lastTyped.name());
    }

    @Test
    void businessExceptionPropagatesAsIs() {
        SampleListener sample = new SampleListener();
        MethodMqListener boom = methodOf(sample, "boom");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> MqListenerInvoker.invoke(boom, GjMessage.of("t.boom", null, null)));
        assertEquals("业务异常原样透传", e.getMessage());
    }

    @Test
    void rejectsMoreThanOneParam() {
        SampleListener sample = new SampleListener();
        assertThrows(IllegalArgumentException.class, () -> methodOf(sample, "tooManyParams"));
    }

    @Test
    void jsonStringModePassesRawStringPayloadThrough() {
        // String 类型的 payload 应原样透传（toJson 会把纯字符串再包一层引号）
        SampleListener sample = new SampleListener();
        MethodMqListener json = methodOf(sample, "json");
        MqListenerInvoker.invoke(json, GjMessage.of("t.json", null, "hello world"));
        assertEquals("hello world", sample.lastJson);
    }

    @Test
    void rejectsInvalidTopic() {
        SampleListener sample = new SampleListener();
        var method = java.util.Arrays.stream(sample.getClass().getDeclaredMethods())
                .filter(m -> m.getName().equals("noArg")).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> MethodMqListener.of(sample, method, fakeAnnotation("bad topic")));
    }

    private MethodMqListener methodOf(Object bean, String methodName) {
        var method = java.util.Arrays.stream(bean.getClass().getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow();
        MqListener anno = method.getAnnotation(MqListener.class);
        return MethodMqListener.of(bean, method, anno);
    }

    /** 动态伪造 @MqListener（注册期校验非法 topic 用） */
    private static MqListener fakeAnnotation(String topic) {
        return (MqListener) java.lang.reflect.Proxy.newProxyInstance(
                MqListener.class.getClassLoader(), new Class<?>[]{MqListener.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "topic" -> topic;
                    case "group" -> "default";
                    case "concurrency" -> -1;
                    case "queue" -> "";
                    case "annotationType" -> MqListener.class;
                    default -> method.getDefaultValue();
                });
    }
}
