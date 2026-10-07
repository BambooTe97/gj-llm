package com.gj.llm.netty.handler;

import com.gj.llm.netty.TestSupport;
import com.gj.llm.netty.config.GjNettyProperties;
import com.gj.llm.netty.dispatch.DispatchService;
import com.gj.llm.netty.dispatch.ListenerRegistry;
import com.gj.llm.netty.dispatch.OrderedJobQueue;
import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.MessageCodec;
import com.gj.llm.netty.protocol.MessageEnvelope;
import com.gj.llm.netty.protocol.Topic;
import com.gj.llm.netty.session.ClientSession;
import com.gj.llm.netty.session.SessionRegistry;
import com.gj.llm.netty.session.SessionRegistryTest;
import com.gj.llm.netty.session.SubscriptionRegistry;
import com.gj.llm.netty.spi.ClientPrincipal;
import com.gj.llm.netty.spi.MessageListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * topic 派发 handler 单测：精确/前缀匹配、未知 topic、sys.subscribe 订阅、慢消费者断开。
 */
class TopicDispatchHandlerTest {

    private GjNettyProperties properties;
    private DispatchService dispatchService;
    private ListenerRegistry listenerRegistry;
    private SubscriptionRegistry subscriptionRegistry;
    private SessionRegistry sessionRegistry;
    private ClientSession session;

    @BeforeAll
    static void initJson() {
        TestSupport.initJackson();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new GjNettyProperties();
        properties.getDispatch().setMode(GjNettyProperties.DispatchMode.DIRECT); // 内联，测试确定性
        NettyMetrics metrics = SessionRegistryTest.metrics();
        ObjectProvider<MessageListener> none = mock(ObjectProvider.class);
        when(none.orderedStream()).thenReturn(java.util.stream.Stream.empty());
        dispatchService = new DispatchService(properties, metrics);
        listenerRegistry = new ListenerRegistry(none);
        subscriptionRegistry = new SubscriptionRegistry();
        sessionRegistry = new SessionRegistry(properties, metrics);
        session = new ClientSession(new EmbeddedChannel(),
                new ClientPrincipal("u1", "user", Map.of()), dispatchService.newQueue());
        session.getChannel().attr(SessionRegistry.SESSION_KEY).set(session);
    }

    private ChannelHandler[] pipelineHandlers() {
        // 带上 MessageEncoder：出站 MessageEnvelope → TextWebSocketFrame（与生产 pipeline 一致）
        return new ChannelHandler[]{new MessageEncoder(properties, SessionRegistryTest.metrics()), handler()};
    }

    private TopicDispatchHandler handler() {
        return new TopicDispatchHandler(listenerRegistry, dispatchService, subscriptionRegistry,
                sessionRegistry, SessionRegistryTest.metrics());
    }

    private static MessageEnvelope envelope(String json) {
        return MessageCodec.decode(json);
    }

    @Test
    void exactAndPrefixListenersBothInvoked() {
        List<String> received = new CopyOnWriteArrayList<>();
        listenerRegistry.register(new MessageListener() {
            @Override
            public String topic() {
                return "chat.content";
            }

            @Override
            public void onMessage(ClientSession s, MessageEnvelope m) {
                received.add("exact:" + ((JsonNode) m.payload()).path("delta").asText());
            }
        });
        listenerRegistry.register(new MessageListener() {
            @Override
            public String topic() {
                return "chat.*";
            }

            @Override
            public void onMessage(ClientSession s, MessageEnvelope m) {
                received.add("prefix:" + m.type());
            }
        });

        EmbeddedChannel channel = (EmbeddedChannel) session.getChannel();
        channel.pipeline().addLast(pipelineHandlers());
        channel.writeInbound(envelope(
                "{\"v\":1,\"type\":\"chat.content\",\"payload\":{\"delta\":\"hi\"}}"));

        assertEquals(List.of("exact:hi", "prefix:chat.content"), received, "DIRECT 模式按注册序内联执行");
    }

    @Test
    void unknownTopicIsDroppedSilently() {
        EmbeddedChannel channel = (EmbeddedChannel) session.getChannel();
        channel.pipeline().addLast(handler());
        channel.writeInbound(envelope("{\"v\":1,\"type\":\"foo.bar\",\"payload\":{}}"));
        assertEquals(0, ((EmbeddedChannel) session.getChannel()).outboundMessages().size());
    }

    @Test
    void heartbeatIsSilentlyConsumed() {
        EmbeddedChannel channel = (EmbeddedChannel) session.getChannel();
        channel.pipeline().addLast(handler());
        channel.writeInbound(envelope("{\"v\":1,\"type\":\"sys.heartbeat\"}"));
        assertEquals(0, channel.outboundMessages().size(), "心跳不应产生回包");
        assertTrue(subscriptionRegistry.channelsFor("chat.x").isEmpty());
    }

    @Test
    void subscribeRegistersAndAcks() {
        EmbeddedChannel channel = (EmbeddedChannel) session.getChannel();
        channel.pipeline().addLast(pipelineHandlers());
        channel.writeInbound(envelope(
                "{\"v\":1,\"type\":\"sys.subscribe\",\"payload\":{\"topic\":\"chat.42\"}}"));

        assertEquals(1, subscriptionRegistry.channelsFor("chat.42").size());
        MessageEnvelope ackEnv = MessageCodec.decode(((TextWebSocketFrame) channel.readOutbound()).text());
        assertEquals(Topic.SYS_ACK, ackEnv.type());
        JsonNode json = (JsonNode) ackEnv.payload();
        assertEquals("subscribe", json.path("op").asText());
        assertTrue(json.path("ok").asBoolean());
    }

    @Test
    void subscribeRejectsSystemTopic() {
        EmbeddedChannel channel = (EmbeddedChannel) session.getChannel();
        channel.pipeline().addLast(pipelineHandlers());
        channel.writeInbound(envelope(
                "{\"v\":1,\"type\":\"sys.subscribe\",\"payload\":{\"topic\":\"sys.heartbeat\"}}"));

        assertEquals(0, subscriptionRegistry.channelsFor("sys.heartbeat").size());
        MessageEnvelope ackEnv = MessageCodec.decode(((TextWebSocketFrame) channel.readOutbound()).text());
        JsonNode payload = (JsonNode) ackEnv.payload();
        assertFalse(payload.path("ok").asBoolean(), "sys.* 订阅必须被拒绝");
    }

    @Test
    void overflowClosesSessionAsSlowConsumer() {
        // 有界队列 + 阻塞 listener：制造 enqueue 失败 → SLOW_CONSUMER 断开
        GjNettyProperties virtualProps = new GjNettyProperties();
        virtualProps.getDispatch().setMode(GjNettyProperties.DispatchMode.VIRTUAL);
        virtualProps.getDispatch().setPerSessionQueue(1);
        NettyMetrics metrics = SessionRegistryTest.metrics();
        ObjectProvider<MessageListener> none = mock(ObjectProvider.class);
        when(none.orderedStream()).thenReturn(java.util.stream.Stream.empty());
        DispatchService virtualDispatch = new DispatchService(virtualProps, metrics);
        ClientSession slowSession = new ClientSession(new EmbeddedChannel(),
                new ClientPrincipal("slow", "user", Map.of()), virtualDispatch.newQueue());
        slowSession.getChannel().attr(SessionRegistry.SESSION_KEY).set(slowSession);

        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch firstRunning = new java.util.concurrent.CountDownLatch(1);
        ListenerRegistry blockingRegistry = new ListenerRegistry(none);
        blockingRegistry.register(new MessageListener() {
            @Override
            public String topic() {
                return "chat.block";
            }

            @Override
            public void onMessage(ClientSession s, MessageEnvelope m) {
                firstRunning.countDown();
                try {
                    release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        EmbeddedChannel channel = (EmbeddedChannel) slowSession.getChannel();
        channel.pipeline().addLast(new TopicDispatchHandler(blockingRegistry, virtualDispatch,
                subscriptionRegistry, sessionRegistry, metrics));

        // 第 1 条：占住消费者；第 2 条填满队列（容量1）；第 3 条触发溢出 → SLOW_CONSUMER
        channel.writeInbound(envelope("{\"v\":1,\"type\":\"chat.block\"}"));
        channel.writeInbound(envelope("{\"v\":1,\"type\":\"chat.block\"}"));
        try {
            channel.writeInbound(envelope("{\"v\":1,\"type\":\"chat.block\"}"));
        } catch (Exception ignored) {
            // 溢出后 close 会关闭 EmbeddedChannel，写第三条可能触发异常——两条路径都算断开
        }

        release.countDown();
        Object outbound = channel.readOutbound();
        while (outbound != null && !(outbound instanceof io.netty.handler.codec.http.websocketx.CloseWebSocketFrame)) {
            outbound = channel.readOutbound();
        }
        assertTrue(outbound instanceof io.netty.handler.codec.http.websocketx.CloseWebSocketFrame,
                "派发队列溢出必须以 Close 帧断开");
        assertEquals(1001, ((io.netty.handler.codec.http.websocketx.CloseWebSocketFrame) outbound).statusCode());
    }
}
