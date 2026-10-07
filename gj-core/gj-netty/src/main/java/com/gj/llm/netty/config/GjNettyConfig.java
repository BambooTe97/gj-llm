package com.gj.llm.netty.config;

import com.gj.llm.netty.dispatch.DispatchService;
import com.gj.llm.netty.dispatch.ListenerRegistry;
import com.gj.llm.netty.handler.AuthRateLimiter;
import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.MessageEnvelope;
import com.gj.llm.netty.server.NettyWebSocketServer;
import com.gj.llm.netty.service.PushService;
import com.gj.llm.netty.service.SessionQueryService;
import com.gj.llm.netty.service.impl.PushServiceImpl;
import com.gj.llm.netty.service.impl.SessionQueryServiceImpl;
import com.gj.llm.netty.session.SessionRegistry;
import com.gj.llm.netty.session.SubscriptionRegistry;
import com.gj.llm.netty.spi.ClusterMessageRouter;
import com.gj.llm.netty.spi.ConnectionLifecycleListener;
import com.gj.llm.netty.spi.HandshakeAuthenticator;
import com.gj.llm.netty.spi.OfflineMessagePolicy;
import com.gj.llm.netty.spi.PipelineCustomizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gj-netty 自动装配 —— 与其它 gj 模块一致走 {@code com.gj.llm} 包扫描，
 * 由 {@code gj.netty.enabled=true} 激活（默认关闭，零启动）。
 *
 * <p>SPI 缺省实现：{@link OfflineMessagePolicy} 默认丢弃、{@link ClusterMessageRouter}
 * 默认单机直推，业务可提供自己的 bean 覆盖（{@code @ConditionalOnMissingBean}）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "gj.netty", name = "enabled", havingValue = "true")
public class GjNettyConfig {

    /**
     * 离线消息默认策略：丢弃 + debug 日志（最简语义，业务可覆盖为落库补发）。
     */
    @Bean
    @ConditionalOnMissingBean(OfflineMessagePolicy.class)
    public OfflineMessagePolicy offlineMessagePolicy() {
        return (principalId, message) ->
                log.debug("gj-netty 离线消息丢弃 principalId={} type={} —— 如需补发请实现 OfflineMessagePolicy",
                        principalId, message.type());
    }

    /**
     * 跨节点路由默认实现：单机直推本节点（P2 换 Redis pub/sub 实现，业务无感）。
     */
    @Bean
    @ConditionalOnMissingBean(ClusterMessageRouter.class)
    public ClusterMessageRouter clusterMessageRouter(PushService pushService) {
        return pushService::send;
    }

    @Bean
    public PushService pushService(SessionRegistry sessionRegistry,
                                   SubscriptionRegistry subscriptionRegistry,
                                   OfflineMessagePolicy offlineMessagePolicy,
                                   NettyMetrics metrics) {
        return new PushServiceImpl(sessionRegistry, subscriptionRegistry, offlineMessagePolicy, metrics);
    }

    @Bean
    public SessionQueryService sessionQueryService(SessionRegistry sessionRegistry) {
        return new SessionQueryServiceImpl(sessionRegistry);
    }

    @Bean
    public NettyWebSocketServer nettyWebSocketServer(GjNettyProperties properties,
                                                     SessionRegistry sessionRegistry,
                                                     AuthRateLimiter authRateLimiter,
                                                     SubscriptionRegistry subscriptionRegistry,
                                                     ListenerRegistry listenerRegistry,
                                                     DispatchService dispatchService,
                                                     NettyMetrics metrics,
                                                     ObjectProvider<HandshakeAuthenticator> authenticator,
                                                     ObjectProvider<ConnectionLifecycleListener> lifecycleListeners,
                                                     ObjectProvider<PipelineCustomizer> pipelineCustomizers) {
        return new NettyWebSocketServer(
                properties,
                sessionRegistry,
                authRateLimiter,
                subscriptionRegistry,
                listenerRegistry,
                dispatchService,
                metrics,
                authenticator.getIfAvailable(),
                lifecycleListeners.orderedStream().toList(),
                pipelineCustomizers.orderedStream().toList());
    }
}
