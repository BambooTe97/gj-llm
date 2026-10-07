package com.gj.llm.netty.handler;

import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.InvalidMessageException;
import com.gj.llm.netty.protocol.MessageCodec;
import com.gj.llm.netty.protocol.MessageEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 信封解码 handler —— 文本帧 JSON → {@link MessageEnvelope}。
 * 解码失败（坏 JSON / 版本不符 / topic 非法）只计数丢弃，不关闭连接。
 *
 * @author gj-llm
 */
@Slf4j
@RequiredArgsConstructor
public class MessageDecoder extends MessageToMessageDecoder<TextWebSocketFrame> {

    private final NettyMetrics metrics;

    @Override
    protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame frame, List<Object> out) {
        try {
            out.add(MessageCodec.decode(frame.text()));
        } catch (InvalidMessageException e) {
            metrics.rejected("bad_format");
            log.debug("netty.audit event=bad_format error={}", e.getMessage());
        }
    }
}
