package com.gj.llm.netty.spi;

import com.gj.llm.netty.protocol.MessageEnvelope;

/**
 * 离线消息策略 SPI —— 定向推送时目标 principal 无活跃连接的处理语义。
 *
 * <p>默认实现：丢弃 + debug 日志（对应"推送不可靠、按需补发"的最简语义）；
 * 需要离线补发的业务自行实现（如落库、投递到队列）。</p>
 *
 * @author gj-llm
 */
public interface OfflineMessagePolicy {

    /**
     * 处理一条无法即时送达的推送。
     *
     * @param principalId 目标身份标识（当前无活跃连接）
     * @param message     未能送达的消息
     */
    void handleOffline(String principalId, MessageEnvelope message);
}
