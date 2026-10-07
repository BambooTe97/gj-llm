package com.gj.llm.netty.handler;

import com.gj.llm.netty.dispatch.DispatchService;
import com.gj.llm.netty.dispatch.ListenerRegistry;
import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.CloseReason;
import com.gj.llm.netty.protocol.MessageEnvelope;
import com.gj.llm.netty.protocol.Topic;
import com.gj.llm.netty.session.ClientSession;
import com.gj.llm.netty.session.SessionRegistry;
import static com.gj.llm.netty.session.SessionRegistry.SESSION_KEY;
import com.gj.llm.netty.session.SubscriptionRegistry;
import com.gj.llm.netty.spi.MessageListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * topic 派发 handler —— 上行消息按 topic 路由：
 *
 * <ul>
 *   <li>{@code sys.*}：心跳/回执静默；{@code sys.subscribe / sys.unsubscribe} 维护下行订阅</li>
 *   <li>业务 topic：精确 + 前缀通配匹配 {@link MessageListener}，无匹配计数丢弃</li>
 *   <li>派发经 {@link DispatchService} 进入会话顺序队列；队列满 = 慢消费者，
 *       以 {@code SLOW_CONSUMER} 断开（护栏 #4/#7 的联动）</li>
 * </ul>
 *
 * @author gj-llm
 */
@Slf4j
@RequiredArgsConstructor
public class TopicDispatchHandler extends SimpleChannelInboundHandler<MessageEnvelope> {

    private final ListenerRegistry listenerRegistry;
    private final DispatchService dispatchService;
    private final SubscriptionRegistry subscriptionRegistry;
    private final SessionRegistry sessionRegistry;
    private final NettyMetrics metrics;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MessageEnvelope envelope) {
        ClientSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session == null) {
            log.warn("netty.audit event=message_without_session type={}", envelope.type());
            return;
        }
        String type = envelope.type();
        if (Topic.isSystem(type)) {
            handleSystem(ctx, session, envelope);
            return;
        }
        List<MessageListener> listeners = listenerRegistry.match(type);
        if (listeners.isEmpty()) {
            metrics.rejected("unknown_topic");
            return;
        }
        for (MessageListener listener : listeners) {
            boolean enqueued = dispatchService.submit(session, () -> listener.onMessage(session, envelope));
            if (!enqueued) {
                // 队列满 = 慢消费者：断开保护（不打爆内存）
                metrics.rejected("slow_consumer");
                sessionRegistry.close(session, CloseReason.SLOW_CONSUMER);
                return;
            }
        }
    }

    private void handleSystem(ChannelHandlerContext ctx, ClientSession session, MessageEnvelope envelope) {
        String type = envelope.type();
        switch (type) {
            case Topic.SYS_HEARTBEAT, Topic.SYS_ACK, Topic.SYS_AUTH, Topic.SYS_SERVER_SHUTDOWN -> {
                // 心跳/回执/预留：只作为活动信号，刷新读写空闲，无其他动作
            }
            case Topic.SYS_SUBSCRIBE, Topic.SYS_UNSUBSCRIBE -> {
                String topic = payloadTopic(envelope);
                boolean ok = Topic.SYS_SUBSCRIBE.equals(type)
                        ? subscriptionRegistry.subscribe(ctx.channel(), topic)
                        : true;
                if (Topic.SYS_UNSUBSCRIBE.equals(type)) {
                    subscriptionRegistry.unsubscribe(ctx.channel(), topic);
                }
                if (!ok) {
                    metrics.rejected("bad_topic");
                }
                session.send(MessageEnvelope.of(Topic.SYS_ACK,
                        Map.of("op", Topic.SYS_SUBSCRIBE.equals(type) ? "subscribe" : "unsubscribe",
                                "topic", topic, "ok", ok)));
            }
            default -> log.debug("netty.audit event=unknown_sys_topic type={}", type);
        }
    }

    private String payloadTopic(MessageEnvelope envelope) {
        Object payload = envelope.payload();
        if (payload instanceof JsonNode node) {
            return node.path("topic").asText("");
        }
        return "";
    }
}
