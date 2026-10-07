package com.gj.llm.netty.handler;

import com.gj.llm.netty.config.GjNettyProperties;
import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.HandshakeRequest;
import com.gj.llm.netty.session.ClientSession;
import com.gj.llm.netty.session.SessionRegistry;
import com.gj.llm.netty.session.SessionRegistryTest;
import com.gj.llm.netty.spi.ClientPrincipal;
import com.gj.llm.netty.spi.HandshakeAuthenticator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 握手鉴权 handler 单测（EmbeddedChannel，无真实端口）：默认拒绝、放行、失败限流、配额预检。
 */
class HandshakeAuthHandlerTest {

    private GjNettyProperties properties;
    private AuthRateLimiter rateLimiter;
    private SessionRegistry registry;
    private NettyMetrics metrics;

    @BeforeEach
    void setUp() {
        properties = new GjNettyProperties();
        rateLimiter = new AuthRateLimiter(properties);
        metrics = SessionRegistryTest.metrics();
        registry = new SessionRegistry(properties, metrics);
    }

    private static FullHttpRequest request(String uri) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    }

    private HandshakeAuthHandler handler(HandshakeAuthenticator authenticator) {
        return new HandshakeAuthHandler(properties, authenticator, rateLimiter, registry, metrics);
    }

    @Test
    void noAuthenticatorDeniesAllByDefault() {
        EmbeddedChannel channel = new EmbeddedChannel(handler(null));
        channel.writeInbound(request("/ws?token=abc"));

        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.UNAUTHORIZED, response.status());
        assertFalse(channel.isOpen(), "默认拒绝模式下连接必须被关闭");
    }

    @Test
    void successfulAuthForwardsUpgradeRequest() {
        ClientPrincipal principal = new ClientPrincipal("u1", "user", Map.of());
        EmbeddedChannel channel = new EmbeddedChannel(handler(req -> principal));
        FullHttpRequest upgrade = request("/ws?token=abc");
        channel.writeInbound(upgrade);

        assertNull(channel.readOutbound(), "鉴权通过不应产生 HTTP 响应");
        Object forwarded = channel.readInbound();
        assertSame(upgrade, forwarded, "upgrade 请求必须原样放行");
        assertTrue(channel.isOpen());
        assertEquals("u1", channel.attr(SessionRegistry.PRINCIPAL_KEY).get().id());
    }

    @Test
    void nonWsPathResponds404() {
        EmbeddedChannel channel = new EmbeddedChannel(handler(null));
        channel.writeInbound(request("/health"));

        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
    }

    @Test
    void authenticatorExceptionDeniesConnection() {
        EmbeddedChannel channel = new EmbeddedChannel(handler(req -> {
            throw new IllegalStateException("token 服务不可用");
        }));
        channel.writeInbound(request("/ws"));

        assertEquals(HttpResponseStatus.UNAUTHORIZED, ((FullHttpResponse) channel.readOutbound()).status());
    }

    @Test
    void repeatedFailuresTriggerCooldown() {
        AtomicInteger calls = new AtomicInteger();
        // 前 threshold 次返回 401，之后冷却期内直接 429（不再调用鉴权器）
        for (int i = 0; i < properties.getLimits().getAuthFailThreshold(); i++) {
            EmbeddedChannel channel = new EmbeddedChannel(handler(req -> {
                calls.incrementAndGet();
                return null;
            }));
            channel.writeInbound(request("/ws"));
            assertEquals(HttpResponseStatus.UNAUTHORIZED, ((FullHttpResponse) channel.readOutbound()).status());
        }
        // 第 6 次：冷却中
        EmbeddedChannel cooldown = new EmbeddedChannel(handler(req -> {
            calls.incrementAndGet();
            return null;
        }));
        cooldown.writeInbound(request("/ws"));
        assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, ((FullHttpResponse) cooldown.readOutbound()).status());
        assertEquals(properties.getLimits().getAuthFailThreshold(), calls.get(),
                "冷却期内不应再调用鉴权器");
    }

    @Test
    void perIpQuotaPreCheckRejectsBeforeAuth() {
        properties.getLimits().setMaxConnectionsPerIp(2);
        String ip = "unknown"; // EmbeddedChannel 非 InetSocketAddress → 固定 unknown
        for (int i = 0; i < 2; i++) {
            ClientSession session = new ClientSession(new EmbeddedChannel(),
                    new ClientPrincipal("u" + i, "user", Map.of()),
                    com.gj.llm.netty.dispatch.OrderedJobQueue.direct(t -> {}));
            registry.register(session, ip);
        }
        EmbeddedChannel channel = new EmbeddedChannel(handler(req -> new ClientPrincipal("u9", "user", Map.of())));
        channel.writeInbound(request("/ws"));

        assertEquals(HttpResponseStatus.FORBIDDEN, ((FullHttpResponse) channel.readOutbound()).status());
        assertEquals(0, registry.principalConnections("u9"), "配额拒绝不应建立会话");
    }
}
