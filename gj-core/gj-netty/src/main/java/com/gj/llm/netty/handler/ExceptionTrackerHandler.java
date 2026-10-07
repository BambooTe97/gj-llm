package com.gj.llm.netty.handler;

import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.CloseReason;
import com.gj.llm.netty.session.ClientSession;
import com.gj.llm.netty.session.SessionRegistry;
import static com.gj.llm.netty.session.SessionRegistry.SESSION_KEY;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 兜底异常 handler —— pipeline 末端：
 *
 * <ul>
 *   <li>未被任何 handler 消费的入站消息（如客户端二进制帧）在此计数丢弃，防 ByteBuf 泄漏</li>
 *   <li>pipeline 异常结构化审计 + 计数，并按 {@code SERVER_ERROR} 断开</li>
 * </ul>
 *
 * @author gj-llm
 */
@Slf4j
@RequiredArgsConstructor
public class ExceptionTrackerHandler extends SimpleChannelInboundHandler<Object> {

    private final SessionRegistry sessionRegistry;
    private final NettyMetrics metrics;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        // SimpleChannelInboundHandler 已自动释放引用计数消息（防泄漏），此处仅计量
        metrics.rejected("unsupported_frame");
        log.warn("netty.audit event=unsupported_frame class={}", msg.getClass().getName());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("netty.audit event=pipeline_error error={}", cause.toString(), cause);
        metrics.exception(cause);
        ClientSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session != null && session.isActive()) {
            sessionRegistry.close(session, CloseReason.SERVER_ERROR);
        } else {
            ctx.close();
        }
    }
}
