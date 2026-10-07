package com.gj.llm.netty.session;

import com.gj.llm.netty.config.GjNettyProperties;
import com.gj.llm.netty.dispatch.OrderedJobQueue;
import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.CloseReason;
import com.gj.llm.netty.spi.ClientPrincipal;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 会话注册表单测：per-principal / per-IP 配额与注销清理。
 */
public class SessionRegistryTest {

    private GjNettyProperties properties;
    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new GjNettyProperties();
        properties.getLimits().setMaxConnectionsPerPrincipal(2);
        properties.getLimits().setMaxConnectionsPerIp(3);
        registry = new SessionRegistry(properties, metrics());
    }

    @SuppressWarnings("unchecked")
    public static NettyMetrics metrics() {
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> r = mock(ObjectProvider.class);
        when(r.getIfAvailable()).thenReturn(null);
        ObjectProvider<SessionRegistry> s = mock(ObjectProvider.class);
        when(s.getIfAvailable()).thenReturn(null);
        ObjectProvider<com.gj.llm.netty.dispatch.DispatchService> d = mock(ObjectProvider.class);
        when(d.getIfAvailable()).thenReturn(null);
        return new NettyMetrics(r, s, d);
    }

    private static ClientSession session(String principalId) {
        EmbeddedChannel channel = new EmbeddedChannel();
        return new ClientSession(channel, new ClientPrincipal(principalId, "user", Map.of()),
                OrderedJobQueue.direct(t -> {}));
    }

    @Test
    void principalQuotaEnforced() {
        registry.register(session("u1"), "1.1.1.1");
        registry.register(session("u1"), "1.1.1.1");
        assertThrows(QuotaExceededException.class, () -> registry.register(session("u1"), "1.1.1.1"));
    }

    @Test
    void unregisterFreesQuota() {
        ClientSession s1 = session("u1");
        registry.register(s1, "1.1.1.1");
        registry.register(session("u1"), "1.1.1.1");
        registry.unregister(s1);
        assertEquals(1, registry.activeCount());
        registry.register(session("u1"), "1.1.1.1"); // 释放后可再注册
        assertEquals(2, registry.principalConnections("u1"));
    }

    @Test
    void ipCountingAcrossPrincipals() {
        registry.register(session("u1"), "2.2.2.2");
        registry.register(session("u2"), "2.2.2.2");
        assertEquals(2, registry.ipConnections("2.2.2.2"));
        assertEquals(0, registry.ipConnections("3.3.3.3"));
    }

    @Test
    void closeSendsCloseFrameWithReason() {
        ClientSession session = session("u1");
        registry.register(session, "1.1.1.1");
        registry.close(session, CloseReason.IDLE_TIMEOUT);
        EmbeddedChannel channel = (EmbeddedChannel) session.getChannel();
        Object outbound = channel.readOutbound();
        assertInstanceOf(CloseWebSocketFrame.class, outbound);
        assertEquals(CloseReason.IDLE_TIMEOUT.getWsCode(), ((CloseWebSocketFrame) outbound).statusCode());
        assertFalse(channel.isOpen());
    }

    @Test
    void findByPrincipalAndSnapshot() {
        registry.register(session("u1"), "1.1.1.1");
        registry.register(session("u2"), "1.1.1.1");
        assertEquals(1, registry.findByPrincipal("u1").size());
        assertEquals(2, registry.allSessions().size());
        assertNotNull(registry.allSessions().getFirst());
    }

    @Test
    void unregisterIsIdempotentOnMissingIp() {
        ClientSession session = session("u1");
        registry.register(session, null);
        registry.unregister(session);
        registry.unregister(session); // 重复注销不抛异常
        assertEquals(0, registry.activeCount());
    }
}
