package com.gj.llm.netty.service.impl;

import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.MessageEnvelope;
import com.gj.llm.netty.protocol.Topic;
import com.gj.llm.netty.service.PushService;
import com.gj.llm.netty.session.ClientSession;
import com.gj.llm.netty.session.SessionRegistry;
import com.gj.llm.netty.session.SubscriptionRegistry;
import com.gj.llm.netty.spi.OfflineMessagePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Predicate;

/**
 * 推送门面默认实现 —— 全部走本节点会话注册表；跨节点路由由 ClusterMessageRouter 在 P2 引入。
 * 由 {@code GjNettyConfig} 装配（遵循项目分层规范：接口在 {@code service/}，实现在 {@code service/impl/}）。
 *
 * @author gj-llm
 */
@Slf4j
@RequiredArgsConstructor
public class PushServiceImpl implements PushService {

    private final SessionRegistry sessionRegistry;
    private final SubscriptionRegistry subscriptionRegistry;
    private final OfflineMessagePolicy offlineMessagePolicy;
    private final NettyMetrics metrics;

    @Override
    public boolean send(String principalId, MessageEnvelope message) {
        List<ClientSession> sessions = sessionRegistry.findByPrincipal(principalId);
        if (sessions.isEmpty()) {
            metrics.rejected("offline");
            offlineMessagePolicy.handleOffline(principalId, message);
            return false;
        }
        sessions.forEach(session -> session.send(message));
        return true;
    }

    @Override
    public int sendToTopic(String topic, MessageEnvelope message) {
        if (!Topic.isValid(topic) || Topic.isSystem(topic)) {
            metrics.rejected("bad_topic");
            return 0;
        }
        List<io.netty.channel.Channel> channels = subscriptionRegistry.channelsFor(topic);
        channels.forEach(channel -> channel.writeAndFlush(message));
        return channels.size();
    }

    @Override
    public int broadcast(Predicate<ClientSession> filter, MessageEnvelope message) {
        int[] delivered = {0};
        sessionRegistry.allSessions().forEach(session -> {
            if (filter.test(session)) {
                session.send(message);
                delivered[0]++;
            }
        });
        return delivered[0];
    }
}
