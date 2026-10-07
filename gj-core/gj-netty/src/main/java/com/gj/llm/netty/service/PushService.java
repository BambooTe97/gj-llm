package com.gj.llm.netty.service;

import com.gj.llm.netty.protocol.MessageEnvelope;
import com.gj.llm.netty.session.ClientSession;

import java.util.function.Predicate;

/**
 * 下行推送门面 —— 任何业务模块注入即可推送，无需感知底层传输（Netty / 未来其它实现）。
 *
 * <p>三种语义：定向（principalId）、订阅（topic，客户端经 {@code sys.subscribe} 声明）、
 * 条件广播（filter）。不可达时的离线语义由 {@code OfflineMessagePolicy} 决定。</p>
 *
 * @author gj-llm
 */
public interface PushService {

    /**
     * 定向推送给某身份的全部活跃连接。
     *
     * @return false 表示该身份当前无活跃连接（已按离线策略处理）
     */
    boolean send(String principalId, MessageEnvelope message);

    /**
     * 推送给订阅了指定 topic 的全部连接（客户端经 sys.subscribe 订阅）。
     *
     * @return 实际送达的连接数
     */
    int sendToTopic(String topic, MessageEnvelope message);

    /**
     * 条件广播。
     *
     * @return 实际送达的连接数
     */
    int broadcast(Predicate<ClientSession> filter, MessageEnvelope message);
}
