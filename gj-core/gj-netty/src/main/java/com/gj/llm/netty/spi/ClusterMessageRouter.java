package com.gj.llm.netty.spi;

import com.gj.llm.netty.protocol.MessageEnvelope;

/**
 * 跨节点消息路由 SPI —— 多实例部署时，推送发起方与目标连接可能不在同一节点。
 *
 * <p>默认实现（单机模式）：直接投递本节点会话。P2 提供 Redis pub/sub 实现
 * （会话注册表写 Redis，广播到各节点后查本地 Channel 投递），替换后单机/多机切换对业务无感。</p>
 *
 * @author gj-llm
 */
public interface ClusterMessageRouter {

    /**
     * 将一条定向消息路由到目标 principal 所在节点。
     */
    void route(String principalId, MessageEnvelope message);
}
