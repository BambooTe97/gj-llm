package com.gj.llm.mq.spi;

import com.gj.llm.mq.core.MethodMqListener;

import java.util.List;

/**
 * 消费端适配接口 —— 把 {@code @MqListener} 方法注册进厂商原生消费容器。
 *
 * <p>实现方约定：bind 时按 {@code group + topic} 分组，同组多方法告警并仅绑首个（竞争消费）；
 * 实现类应自行管理容器生命周期（实现 DisposableBean 在停机时释放）；
 * 异常语义统一为"至少一次"（见 {@code @MqListener} javadoc）。</p>
 *
 * @author gj-llm
 */
public interface MqConsumerBinder {

    /** 注册所有 @MqListener 方法（单例全部实例化后由 MqListenerRegistrar 调用一次） */
    void bind(List<MethodMqListener> listeners);
}
