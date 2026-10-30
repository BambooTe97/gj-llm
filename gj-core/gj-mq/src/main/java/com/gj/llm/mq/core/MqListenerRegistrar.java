package com.gj.llm.mq.core;

import com.gj.llm.mq.annotation.MqListener;
import com.gj.llm.mq.spi.MqConsumerBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@code @MqListener} 扫描注册器 —— BeanPostProcessor 收集所有监听方法，
 * 单例全部实例化后（{@link SmartInitializingSingleton}）一次性交给当前选型的
 * {@link MqConsumerBinder} 建容器。
 *
 * <p>无可用 Binder（未选型 / 客户端依赖缺失）时只告警不阻断启动 ——
 * 消息收发是旁路能力，不能把主业务堵在启动上。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "gj.mq", name = "enabled", havingValue = "true")
public class MqListenerRegistrar implements BeanPostProcessor, SmartInitializingSingleton {

    private final ObjectProvider<MqConsumerBinder> binderProvider;
    private final List<MethodMqListener> listeners = new CopyOnWriteArrayList<>();

    public MqListenerRegistrar(ObjectProvider<MqConsumerBinder> binderProvider) {
        this.binderProvider = binderProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        // 沿父类链扫描（@MqListener 写在基类模板方法上同样生效；子类覆盖父类时以子类方法为准）
        for (Method method : listenerCandidateMethods(targetClass).values()) {
            MqListener anno = AnnotatedElementUtils.findMergedAnnotation(method, MqListener.class);
            if (anno != null) {
                listeners.add(MethodMqListener.of(bean, method, anno));
                log.info("[gj-mq] 注册消费者: {}#{}, topic={}, group={}",
                        targetClass.getSimpleName(), method.getName(), anno.topic(), anno.group());
            }
        }
        return bean;
    }

    /**
     * 收集目标类及其父类链上的全部方法，同签名（名称+参数）以子类声明为准
     */
    private Map<String, Method> listenerCandidateMethods(Class<?> targetClass) {
        Map<String, Method> bySignature = new LinkedHashMap<>();
        for (Class<?> cls = targetClass; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Method method : cls.getDeclaredMethods()) {
                bySignature.putIfAbsent(
                        method.getName() + java.util.Arrays.toString(method.getParameterTypes()), method);
            }
        }
        return bySignature;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (listeners.isEmpty()) {
            log.debug("[gj-mq] 未发现 @MqListener，跳过消费绑定");
            return;
        }
        MqConsumerBinder binder = binderProvider.getIfAvailable();
        if (binder == null) {
            log.warn("[gj-mq] 检测到 {} 个 @MqListener 但无可用 MqConsumerBinder"
                            + "（gj.mq.type 未配置或对应客户端依赖缺失），监听器暂不生效",
                    listeners.size());
            return;
        }
        binder.bind(List.copyOf(listeners));
    }
}
