package com.gj.llm.netty.session;

import com.gj.llm.netty.protocol.Topic;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 下行 topic 订阅注册表 —— 客户端通过 {@code sys.subscribe} / {@code sys.unsubscribe}
 * 声明关心的 topic，服务端经 {@code PushService#sendToTopic} 按订阅推送。
 *
 * <p>订阅关系随连接断开自动清理。业务 topic 允许订阅；{@code sys.*} 拒绝。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "gj.netty", name = "enabled", havingValue = "true")
public class SubscriptionRegistry {

    /** 连接上的订阅 topic 集合（随连接清理） */
    private static final AttributeKey<Set<String>> SUBSCRIPTIONS_KEY = AttributeKey.valueOf("gj.netty.subscriptions");

    /** topic → 订阅连接集合 */
    private final ConcurrentMap<String, CopyOnWriteArraySet<Channel>> byTopic = new ConcurrentHashMap<>();

    /**
     * 订阅一个业务 topic。
     *
     * @return false 表示 topic 非法或为保留命名空间
     */
    public boolean subscribe(Channel channel, String topic) {
        if (!Topic.isValid(topic) || Topic.isSystem(topic)) {
            return false;
        }
        byTopic.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(channel);
        subscriptionsOf(channel).add(topic);
        log.debug("netty.audit event=subscribe channel={} topic={}", channel.id(), topic);
        return true;
    }

    /**
     * 取消订阅。
     */
    public void unsubscribe(Channel channel, String topic) {
        Set<Channel> channels = byTopic.get(topic);
        if (channels != null) {
            channels.remove(channel);
            if (channels.isEmpty()) {
                byTopic.remove(topic, channels);
            }
        }
        subscriptionsOf(channel).remove(topic);
    }

    /** 订阅了指定 topic 的连接快照 */
    public List<Channel> channelsFor(String topic) {
        Set<Channel> channels = byTopic.get(topic);
        return channels == null ? List.of() : List.copyOf(channels);
    }

    /** 连接断开时清理其全部订阅 */
    public void unregisterChannel(Channel channel) {
        Set<String> topics = channel.attr(SUBSCRIPTIONS_KEY).get();
        if (topics != null) {
            topics.forEach(topic -> unsubscribe(channel, topic));
            channel.attr(SUBSCRIPTIONS_KEY).set(null);
        }
    }

    private Set<String> subscriptionsOf(Channel channel) {
        // Netty Attribute 无 computeIfAbsent，用 setIfAbsent 处理并发首建
        Set<String> existing = channel.attr(SUBSCRIPTIONS_KEY).get();
        if (existing != null) {
            return existing;
        }
        Set<String> created = ConcurrentHashMap.newKeySet();
        Set<String> raced = channel.attr(SUBSCRIPTIONS_KEY).setIfAbsent(created);
        return raced == null ? created : raced;
    }
}
