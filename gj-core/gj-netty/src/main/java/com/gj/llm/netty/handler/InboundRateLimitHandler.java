package com.gj.llm.netty.handler;

import com.gj.llm.netty.metrics.NettyMetrics;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 上行限速 handler —— 每连接每秒消息数上限，超速帧丢弃并计数（护栏 #7）。
 * 防恶意拉流/打爆派发队列；持续滥用由上游的鉴权与配额护栏处置。
 *
 * <p>handler 为每连接一个实例（initChannel 中创建），窗口状态直接放实例字段。</p>
 *
 * @author gj-llm
 */
@RequiredArgsConstructor
public class InboundRateLimitHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final int limitPerSecond;
    private final NettyMetrics metrics;

    private volatile long windowStart = System.currentTimeMillis();
    private final AtomicInteger windowCount = new AtomicInteger();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000) {
            windowStart = now;
            windowCount.set(0);
        }
        if (windowCount.incrementAndGet() > limitPerSecond) {
            metrics.rejected("rate_limit");
            return; // 丢弃超速帧，不关闭连接（持续滥用由配额/冷却护栏处置）
        }
        ctx.fireChannelRead(frame.retain());
    }
}
