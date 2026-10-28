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
import java.util.List;
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
        for (Method method : targetClass.getDeclaredMethods()) {
            MqListener anno = AnnotatedElementUtils.findMergedAnnotation(method, MqListener.class);
            if (anno != null) {
                listeners.add(MethodMqListener.of(bean, method, anno));
                log.info("[gj-mq] 注册消费者: {}#{}, topic={}, group={}",
                        targetClass.getSimpleName(), method.getName(), anno.topic(), anno.group());
            }
        }
        return bean;
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
