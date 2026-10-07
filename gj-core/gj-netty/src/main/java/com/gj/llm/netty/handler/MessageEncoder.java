package com.gj.llm.netty.handler;

import com.gj.llm.netty.config.GjNettyProperties;
import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.MessageCodec;
import com.gj.llm.netty.protocol.MessageEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 信封编码 handler —— {@link MessageEnvelope} → 文本帧。
 *
 * <p>同时是出站计量单点：消息发送量、以及<b>下行软告警</b>——单条推送超过
 * 帧长上限（默认 64KB）打 WARN + 计数，不拦截（大 payload 应走 HTTP，见 GUIDE 第五节）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@RequiredArgsConstructor
public class MessageEncoder extends MessageToMessageEncoder<MessageEnvelope> {

    private final GjNettyProperties properties;
    private final NettyMetrics metrics;

    @Override
    protected void encode(ChannelHandlerContext ctx, MessageEnvelope envelope, List<Object> out) {
        String json = MessageCodec.encode(envelope);
        metrics.messageSent();
        if (json.length() > properties.getLimits().getMaxFrameBytes()) {
            metrics.outboundOversize();
            log.warn("netty.audit event=outbound_oversize bytes={} type={} —— 大 payload 应走 HTTP 下载或分片",
                    json.length(), envelope.type());
        }
        out.add(new TextWebSocketFrame(json));
    }
}
