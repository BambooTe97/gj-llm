package com.gj.llm.netty.server;

import com.gj.llm.netty.config.GjNettyProperties;
import com.gj.llm.netty.dispatch.DispatchService;
import com.gj.llm.netty.dispatch.ListenerRegistry;
import com.gj.llm.netty.handler.ExceptionTrackerHandler;
import com.gj.llm.netty.handler.HandlerNames;
import com.gj.llm.netty.handler.HandshakeAuthHandler;
import com.gj.llm.netty.handler.IdleHeartbeatHandler;
import com.gj.llm.netty.handler.InboundRateLimitHandler;
import com.gj.llm.netty.handler.MessageDecoder;
import com.gj.llm.netty.handler.MessageEncoder;
import com.gj.llm.netty.handler.AuthRateLimiter;
import com.gj.llm.netty.handler.SessionLifecycleHandler;
import com.gj.llm.netty.handler.TopicDispatchHandler;
import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.MessageEnvelope;
import com.gj.llm.netty.session.ClientSession;
import com.gj.llm.netty.session.SessionRegistry;
import com.gj.llm.netty.session.SubscriptionRegistry;
import com.gj.llm.netty.spi.ConnectionLifecycleListener;
import com.gj.llm.netty.spi.HandshakeAuthenticator;
import com.gj.llm.netty.spi.PipelineCustomizer;
import com.gj.llm.netty.spi.PipelinePhase;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.ResourceLeakDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Netty WebSocket 服务器 —— 独立端口传输底座（默认 9090），随 Spring 生命周期启停。
 *
 * <p>pipeline 装配顺序见 {@code HandlerNames}；护栏默认值见 {@code GJ_NETTY_GUIDE.md} 第七节。
 * 优雅停机语义：停接受新连接 → 通知全部客户端（sys.server.shutdown）→ 等待 drain
 * （默认 10s）→ 关闭全部连接 → 释放线程组与派发资源。</p>
 *
 * @author gj-llm
 */
@Slf4j
public class NettyWebSocketServer implements SmartLifecycle {

    /** 出站写缓冲水位：慢消费者观测阈值（512KB / 1MB） */
    private static final int WRITE_BUFFER_LOW_WATER_MARK = 512 * 1024;
    private static final int WRITE_BUFFER_HIGH_WATER_MARK = 1024 * 1024;

    private final GjNettyProperties properties;
    private final SessionRegistry sessionRegistry;
    private final AuthRateLimiter authRateLimiter;
    private final SubscriptionRegistry subscriptionRegistry;
    private final ListenerRegistry listenerRegistry;
    private final DispatchService dispatchService;
    private final NettyMetrics metrics;
    private final HandshakeAuthenticator authenticator;
    private final List<ConnectionLifecycleListener> lifecycleListeners;
    private final List<PipelineCustomizer> pipelineCustomizers;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running;

    public NettyWebSocketServer(GjNettyProperties properties,
                                SessionRegistry sessionRegistry,
                                AuthRateLimiter authRateLimiter,
                                SubscriptionRegistry subscriptionRegistry,
                                ListenerRegistry listenerRegistry,
                                DispatchService dispatchService,
                                NettyMetrics metrics,
                                HandshakeAuthenticator authenticator,
                                List<ConnectionLifecycleListener> lifecycleListeners,
                                List<PipelineCustomizer> pipelineCustomizers) {
        this.properties = properties;
        this.sessionRegistry = sessionRegistry;
        this.authRateLimiter = authRateLimiter;
        this.subscriptionRegistry = subscriptionRegistry;
        this.listenerRegistry = listenerRegistry;
        this.dispatchService = dispatchService;
        this.metrics = metrics;
        this.authenticator = authenticator;
        this.lifecycleListeners = lifecycleListeners;
        this.pipelineCustomizers = pipelineCustomizers;
    }

    // ==================== 生命周期（SmartLifecycle） ====================

    @Override
    public void start() {
        if (running) {
            return;
        }
        applyLeakDetection();
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new io.netty.channel.WriteBufferWaterMark(
                        WRITE_BUFFER_LOW_WATER_MARK, WRITE_BUFFER_HIGH_WATER_MARK))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        initPipeline(ch);
                    }
                });
        try {
            serverChannel = bootstrap.bind(properties.getPort()).syncUninterruptibly().channel();
        } catch (RuntimeException e) {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            throw new IllegalStateException("gj-netty 端口绑定失败: " + properties.getPort(), e);
        }
        running = true;
        log.info("gj-netty WS 服务器已启动 port={} path={} auth={} 定制器={}",
                properties.getPort(), properties.getPath(),
                authenticator == null ? "无(默认拒绝)" : authenticator.getClass().getSimpleName(),
                pipelineCustomizers.size());
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        long graceSeconds = properties.getShutdown().getGraceSeconds();
        log.info("gj-netty 开始优雅停机: 停止接受新连接，drain 等待 {}s", graceSeconds);

        // 1. 停止接受新连接
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }

        // 2. 通知全部客户端并安排延迟关闭（给在途消息 drain 窗口）
        MessageEnvelope shutdownNotice = MessageEnvelope.serverShutdown();
        for (ClientSession session : sessionRegistry.allSessions()) {
            session.send(shutdownNotice);
            session.getChannel().eventLoop().schedule(
                    () -> sessionRegistry.close(session, com.gj.llm.netty.protocol.CloseReason.SERVER_SHUTDOWN),
                    graceSeconds, TimeUnit.SECONDS);
        }

        // 3. 释放线程组（shutdownGracefully 先拒绝新任务再排空在途）
        try {
            workerGroup.shutdownGracefully(graceSeconds, graceSeconds + 10, TimeUnit.SECONDS)
                    .await(graceSeconds + 15, TimeUnit.SECONDS);
            bossGroup.shutdownGracefully().await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 4. 释放派发层共享资源（pool 模式执行器）
        dispatchService.shutdown();
        log.info("gj-netty 已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 默认阶段即可：晚于大多数 bean 初始化，早于容器销毁
        return SmartLifecycle.DEFAULT_PHASE;
    }

    // ==================== pipeline 装配 ====================

    private void initPipeline(SocketChannel ch) {
        int maxFrameBytes = properties.getLimits().getMaxFrameBytes();
        var pipeline = ch.pipeline();
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(maxFrameBytes));
        pipeline.addLast(HandlerNames.HANDSHAKE_AUTH,
                new HandshakeAuthHandler(properties, authenticator, authRateLimiter, sessionRegistry, metrics));
        pipeline.addLast(new WebSocketServerProtocolHandler(WebSocketServerProtocolConfig.newBuilder()
                .websocketPath(properties.getPath())
                .maxFramePayloadLength(maxFrameBytes)      // 护栏 #1：上行帧长硬限制
                .allowExtensions(true)                     // permessage-deflate
                .build()));
        pipeline.addLast(HandlerNames.SESSION_LIFECYCLE, new SessionLifecycleHandler(
                sessionRegistry, dispatchService, subscriptionRegistry, metrics, lifecycleListeners));
        pipeline.addLast(HandlerNames.IDLE_HEARTBEAT, new IdleHeartbeatHandler(properties, sessionRegistry));
        pipeline.addLast(HandlerNames.INBOUND_RATE_LIMIT, new InboundRateLimitHandler(
                properties.getLimits().getInboundRatePerConn(), metrics));
        pipeline.addLast(HandlerNames.MESSAGE_DECODER, new MessageDecoder(metrics));
        pipeline.addLast(HandlerNames.MESSAGE_ENCODER, new MessageEncoder(properties, metrics));
        // CODEC 定制区（PipelineCustomizer 在 gjMessageDecoder 之后插入）
        customizePipeline(pipeline, PipelinePhase.CODEC);
        pipeline.addLast(HandlerNames.TOPIC_DISPATCH, new TopicDispatchHandler(
                listenerRegistry, dispatchService, subscriptionRegistry, sessionRegistry, metrics));
        // DISPATCH 定制区（gjTopicDispatch 之前插入）
        customizePipeline(pipeline, PipelinePhase.DISPATCH);
        pipeline.addLast(HandlerNames.EXCEPTION_TRACKER, new ExceptionTrackerHandler(sessionRegistry, metrics));
    }

    private void customizePipeline(io.netty.channel.ChannelPipeline pipeline, PipelinePhase phase) {
        for (PipelineCustomizer customizer : pipelineCustomizers) {
            try {
                customizer.customize(pipeline, phase);
            } catch (Exception e) {
                log.warn("gj-netty PipelineCustomizer 执行失败 phase={} customizer={}",
                        phase, customizer.getClass().getName(), e);
            }
        }
    }

    private void applyLeakDetection() {
        // 护栏 #5：内存泄漏防护，dev 建议 paranoid（护栏默认值见 GJ_NETTY_GUIDE.md 第七节）
        String level = switch (properties.getLeakDetection()) {
            case PARANOID -> "paranoid";
            case SIMPLE -> "simple";
            case DISABLED -> "disabled";
        };
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.valueOf(level.toUpperCase()));
    }
}
