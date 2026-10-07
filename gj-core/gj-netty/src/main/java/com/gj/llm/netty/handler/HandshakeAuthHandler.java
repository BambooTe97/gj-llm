package com.gj.llm.netty.handler;

import com.gj.llm.netty.config.GjNettyProperties;
import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.HandshakeRequest;
import com.gj.llm.netty.session.SessionRegistry;
import static com.gj.llm.netty.session.SessionRegistry.PRINCIPAL_KEY;
import static com.gj.llm.netty.session.SessionRegistry.REMOTE_IP_KEY;
import com.gj.llm.netty.spi.ClientPrincipal;
import com.gj.llm.netty.spi.HandshakeAuthenticator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 握手鉴权 handler —— 独立端口不经过 Spring Security 过滤器链，鉴权在此拦截（护栏体系入口）：
 *
 * <ol>
 *   <li>路径校验：非 WS 路径直接 404（健康探测等到达此端口时快速失败）</li>
 *   <li>鉴权失败限流：冷却期内直接 429（护栏 #3）</li>
 *   <li>per-IP 连接配额预检：超限 403（护栏 #2；反代场景说明见 GUIDE 第七节）</li>
 *   <li>{@code HandshakeAuthenticator} 鉴权：<b>无实现 bean = 默认拒绝所有连接</b></li>
 * </ol>
 *
 * <p>通过后把来源 IP 与 {@link ClientPrincipal} 写入 Channel 属性，放行 upgrade 请求。</p>
 *
 * @author gj-llm
 */
@Slf4j
public class HandshakeAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final GjNettyProperties properties;
    private final HandshakeAuthenticator authenticator; // 可为 null：默认拒绝
    private final AuthRateLimiter rateLimiter;
    private final SessionRegistry registry;
    private final NettyMetrics metrics;

    public HandshakeAuthHandler(GjNettyProperties properties,
                                HandshakeAuthenticator authenticator,
                                AuthRateLimiter rateLimiter,
                                SessionRegistry registry,
                                NettyMetrics metrics) {
        this.properties = properties;
        this.authenticator = authenticator;
        this.rateLimiter = rateLimiter;
        this.registry = registry;
        this.metrics = metrics;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = new QueryStringDecoder(request.uri()).path();
        if (!properties.getPath().equals(path)) {
            log.warn("netty.audit event=not_found ip={} uri={}", remoteIp(ctx), request.uri());
            respondAndClose(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        String ip = remoteIp(ctx);

        // 1. 鉴权失败冷却期（护栏 #3）
        if (rateLimiter.isCoolingDown(ip)) {
            metrics.rejected("auth_cooldown");
            log.warn("netty.audit event=auth_cooldown ip={}", ip);
            respondAndClose(ctx, HttpResponseStatus.TOO_MANY_REQUESTS);
            return;
        }

        // 2. per-IP 连接配额预检（护栏 #2）
        if (registry.ipConnections(ip) >= properties.getLimits().getMaxConnectionsPerIp()) {
            metrics.rejected("quota");
            log.warn("netty.audit event=quota_exceeded scope=ip ip={}", ip);
            respondAndClose(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }

        // 3. 鉴权（默认拒绝：无 authenticator = 拒绝所有连接）
        ClientPrincipal principal = null;
        if (authenticator != null) {
            try {
                principal = authenticator.authenticate(toHandshakeRequest(request));
            } catch (Exception e) {
                log.debug("netty.audit event=auth_error ip={} error={}", ip, e.getMessage());
            }
        } else {
            log.warn("netty.audit event=auth_denied_no_authenticator ip={}", ip);
        }
        if (principal == null) {
            rateLimiter.recordFailure(ip);
            metrics.authFailure();
            log.warn("netty.audit event=auth_failed ip={} path={}", ip, path);
            respondAndClose(ctx, HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        // 4. 放行 upgrade
        rateLimiter.reset(ip);
        ctx.channel().attr(REMOTE_IP_KEY).set(ip);
        ctx.channel().attr(PRINCIPAL_KEY).set(principal);
        ctx.fireChannelRead(request.retain());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("netty.audit event=handshake_error ip={} error={}", remoteIp(ctx), cause.toString());
        ctx.close();
    }

    private HandshakeRequest toHandshakeRequest(FullHttpRequest request) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        Map<String, String> queryParams = new HashMap<>();
        decoder.parameters().forEach((k, v) -> {
            List<String> values = v;
            if (values != null && !values.isEmpty()) {
                queryParams.put(k, values.getFirst());
            }
        });
        Map<String, String> headers = new HashMap<>();
        request.headers().forEach(e -> headers.put(e.getKey().toLowerCase(), e.getValue()));
        return new HandshakeRequest(decoder.path(), Map.copyOf(queryParams), Map.copyOf(headers));
    }

    private String remoteIp(ChannelHandlerContext ctx) {
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress remote) {
            return remote.getAddress().getHostAddress();
        }
        return "unknown"; // 非 TCP 传输（如测试用 EmbeddedChannel）
    }

    private void respondAndClose(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
