package com.gj.llm.netty.handler;

import com.gj.llm.netty.dispatch.DispatchService;
import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.CloseReason;
import com.gj.llm.netty.session.ClientSession;
import com.gj.llm.netty.session.QuotaExceededException;
import com.gj.llm.netty.session.SessionRegistry;
import com.gj.llm.netty.session.SubscriptionRegistry;
import static com.gj.llm.netty.session.SessionRegistry.PRINCIPAL_KEY;
import static com.gj.llm.netty.session.SessionRegistry.REMOTE_IP_KEY;
import static com.gj.llm.netty.session.SessionRegistry.SESSION_KEY;
import static com.gj.llm.netty.session.SessionRegistry.CLOSE_REASON_KEY;
import com.gj.llm.netty.spi.ClientPrincipal;
import com.gj.llm.netty.spi.ConnectionLifecycleListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 会话生命周期 handler —— upgrade 完成时创建/注册 {@link ClientSession}，
 * 连接断开时注销并回调 {@link ConnectionLifecycleListener}。
 *
 * <p>同时负责慢消费者观测：写出水位超限（不可写）时递增 {@code gj.netty.slow.consumers} 指标。
 * 回调都在 EventLoop 上，listener 实现禁止阻塞。</p>
 *
 * @author gj-llm
 */
@Slf4j
@RequiredArgsConstructor
public class SessionLifecycleHandler extends ChannelInboundHandlerAdapter {

    private final SessionRegistry registry;
    private final DispatchService dispatchService;
    private final SubscriptionRegistry subscriptionRegistry;
    private final NettyMetrics metrics;
    private final List<ConnectionLifecycleListener> lifecycleListeners;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete complete) {
            onHandshakeComplete(ctx, complete);
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    private void onHandshakeComplete(ChannelHandlerContext ctx, WebSocketServerProtocolHandler.HandshakeComplete event) {
        ClientPrincipal principal = ctx.channel().attr(PRINCIPAL_KEY).get();
        if (principal == null) {
            // 理论不可达：HandshakeAuthHandler 保证 principal 存在；防御性处理
            log.warn("netty.audit event=handshake_without_principal");
            ctx.close();
            return;
        }
        String ip = ctx.channel().attr(REMOTE_IP_KEY).get();
        ClientSession session = new ClientSession(ctx.channel(), principal, dispatchService.newQueue());
        try {
            registry.register(session, ip == null ? "unknown" : ip);
        } catch (QuotaExceededException e) {
            log.warn("netty.audit event=quota_exceeded scope=principal principalId={}", principal.id());
            ctx.channel().attr(CLOSE_REASON_KEY).set(CloseReason.QUOTA_EXCEEDED);
            ctx.writeAndFlush(new CloseWebSocketFrame(CloseReason.QUOTA_EXCEEDED.getWsCode(),
                    CloseReason.QUOTA_EXCEEDED.getPhrase())).addListener(future -> ctx.close());
            return;
        }
        for (ConnectionLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onConnected(session);
            } catch (Exception e) {
                log.warn("gj-netty onConnected 回调异常 listener={}", listener.getClass().getName(), e);
            }
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (!ctx.channel().isWritable()) {
            metrics.slowConsumerEnter();
        } else {
            metrics.slowConsumerLeave();
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ClientSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session != null) {
            CloseReason reason = ctx.channel().attr(CLOSE_REASON_KEY).get();
            if (reason == null) {
                reason = CloseReason.CLIENT_CLOSE;
            }
            for (ConnectionLifecycleListener listener : lifecycleListeners) {
                try {
                    listener.onDisconnected(session, reason);
                } catch (Exception e) {
                    log.warn("gj-netty onDisconnected 回调异常 listener={}", listener.getClass().getName(), e);
                }
            }
            subscriptionRegistry.unregisterChannel(ctx.channel());
            dispatchService.closeQueue(session.getJobQueue());
            registry.unregister(session);
            ctx.channel().attr(SESSION_KEY).set(null);
        }
        ctx.fireChannelInactive();
    }
}
