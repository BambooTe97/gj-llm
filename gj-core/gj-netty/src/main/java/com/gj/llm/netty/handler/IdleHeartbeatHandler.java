package com.gj.llm.netty.handler;

import com.gj.llm.netty.config.GjNettyProperties;
import com.gj.llm.netty.protocol.CloseReason;
import com.gj.llm.netty.protocol.MessageEnvelope;
import com.gj.llm.netty.session.ClientSession;
import com.gj.llm.netty.session.SessionRegistry;
import static com.gj.llm.netty.session.SessionRegistry.SESSION_KEY;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 空闲心跳 handler —— 半开连接清理（护栏 #6）：
 *
 * <ul>
 *   <li><b>读空闲</b>（默认 60s）：对端无任何流量 → 按半开连接断开（{@code IDLE_TIMEOUT}）</li>
 *   <li><b>写空闲</b>（默认 30s）：服务端主动发 {@code sys.heartbeat} ping；
 *       客户端回复同名消息即刷新读空闲</li>
 * </ul>
 *
 * <p>读/写双向空闲检测缺一不可：移动网络下 TCP 半开连接是常态而非异常。</p>
 *
 * @author gj-llm
 */
@Slf4j
public class IdleHeartbeatHandler extends IdleStateHandler {

    private final SessionRegistry registry;

    public IdleHeartbeatHandler(GjNettyProperties properties, SessionRegistry registry) {
        super(properties.getHeartbeat().getReadIdleSeconds(),
                properties.getHeartbeat().getWriteIdleSeconds(), 0);
        this.registry = registry;
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
        ClientSession session = ctx.channel().attr(SESSION_KEY).get();
        if (evt.state() == IdleState.READER_IDLE) {
            log.info("netty.audit event=idle_timeout sessionId={}",
                    session == null ? "unknown" : session.sessionId());
            if (session != null) {
                ctx.channel().attr(SessionRegistry.CLOSE_REASON_KEY).set(CloseReason.IDLE_TIMEOUT);
                registry.close(session, CloseReason.IDLE_TIMEOUT);
            } else {
                ctx.close();
            }
        } else if (evt.state() == IdleState.WRITER_IDLE && session != null) {
            session.send(MessageEnvelope.heartbeat());
        }
    }
}
