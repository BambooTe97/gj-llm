package com.gj.llm.mq.spi;

import com.gj.llm.mq.model.GjMessage;
import com.gj.llm.mq.model.SendResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 生产端适配接口 —— 每个具体 MQ 实现一个实现类，把统一信封翻译为厂商客户端调用。
 *
 * <p>实现方约定：send 失败抛 RuntimeException（门面统一记指标后原样上抛）；
 * sendAsync 不阻塞调用线程；sendDelay 不支持时由 capability 声明，门面先行拦截。</p>
 *
 * @author gj-llm
 */
public interface MqProducerBinder {

    /** 能力声明（延迟/顺序/事务），门面据此做能力前置校验 */
    MqCapability capability();

    /** 同步发送：成功送达 Broker 即返回 */
    SendResult send(GjMessage message);

    /** 异步发送：不阻塞调用线程，回执经 future 返回 */
    CompletableFuture<SendResult> sendAsync(GjMessage message);

    /** 延迟投递：实现不支持时抛 UnsupportedOperationException */
    void sendDelay(GjMessage message, Duration delay);
}
