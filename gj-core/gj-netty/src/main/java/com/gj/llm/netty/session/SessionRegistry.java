package com.gj.llm.netty.session;

import com.gj.llm.netty.config.GjNettyProperties;
import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.protocol.CloseReason;
import com.gj.llm.netty.spi.ClientPrincipal;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话注册表 —— 本节点的连接资产台账：
 * principalId → 会话列表（定向推送寻址）、IP → 连接计数（per-IP 配额）、连接审计。
 *
 * <p>注意：per-principal 配额是主闸门（不受 NAT/反代拓扑影响）；per-IP 计数在
 * 反代未配 Proxy Protocol 时等于"全站总闸"，调参见 {@code GJ_NETTY_GUIDE.md} 第七节。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gj.netty", name = "enabled", havingValue = "true")
public class SessionRegistry {

    // ==================== Channel 属性键 ====================

    /** 连接上的会话对象 */
    public static final AttributeKey<ClientSession> SESSION_KEY = AttributeKey.valueOf("gj.netty.session");

    /** 握手时记录的来源 IP（鉴权 handler 写入） */
    public static final AttributeKey<String> REMOTE_IP_KEY = AttributeKey.valueOf("gj.netty.remoteIp");

    /** 握手时记录的客户端身份（鉴权 handler 写入） */
    public static final AttributeKey<ClientPrincipal> PRINCIPAL_KEY = AttributeKey.valueOf("gj.netty.principal");

    /** 关闭原因（close 助手写入，channelInactive 时读取用于审计） */
    public static final AttributeKey<CloseReason> CLOSE_REASON_KEY = AttributeKey.valueOf("gj.netty.closeReason");

    // ==================== 注册表状态 ====================

    /** principalId → 活跃会话列表 */
    private final ConcurrentMap<String, CopyOnWriteArrayList<ClientSession>> byPrincipal = new ConcurrentHashMap<>();

    /** IP → 活跃连接计数 */
    private final ConcurrentMap<String, AtomicInteger> byIp = new ConcurrentHashMap<>();

    private final GjNettyProperties properties;
    private final NettyMetrics metrics;

    // ==================== 注册 / 注销 ====================

    /**
     * 注册会话（握手完成、upgrade 成功后调用），校验 per-principal 配额。
     *
     * @throws QuotaExceededException 单 principal 活跃连接数超上限
     */
    public void register(ClientSession session, String ip) {
        String principalId = session.principalId();
        CopyOnWriteArrayList<ClientSession> sessions =
                byPrincipal.computeIfAbsent(principalId, k -> new CopyOnWriteArrayList<>());
        synchronized (sessions) {
            if (sessions.size() >= properties.getLimits().getMaxConnectionsPerPrincipal()) {
                metrics.rejected("quota");
                throw new QuotaExceededException(
                        "principal [" + principalId + "] 连接数超上限 " + properties.getLimits().getMaxConnectionsPerPrincipal());
            }
            sessions.add(session);
        }
        String effectiveIp = ip == null ? "unknown" : ip;
        // 鉴权 handler 已写入 REMOTE_IP_KEY；此处兜底补写（保证注销时 IP 计数可回退）
        session.getChannel().attr(REMOTE_IP_KEY).setIfAbsent(effectiveIp);
        byIp.computeIfAbsent(effectiveIp, k -> new AtomicInteger()).incrementAndGet();
        session.getChannel().attr(SESSION_KEY).set(session);
        metrics.connectionOpened();
        log.info("netty.audit event=connect sessionId={} principalId={} type={} ip={} active={}",
                session.sessionId(), principalId, session.getPrincipal().type(), ip, activeCount());
    }

    /**
     * 注销会话（连接断开后调用），清理索引与 IP 计数。
     */
    public void unregister(ClientSession session) {
        String principalId = session.principalId();
        CopyOnWriteArrayList<ClientSession> sessions = byPrincipal.get(principalId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                byPrincipal.remove(principalId, sessions);
            }
        }
        String ip = session.getChannel().attr(REMOTE_IP_KEY).get();
        if (ip != null) {
            AtomicInteger count = byIp.get(ip);
            if (count != null && count.decrementAndGet() <= 0) {
                byIp.remove(ip, count);
            }
        }
        metrics.connectionClosed(closeReasonOf(session));
        log.info("netty.audit event=disconnect sessionId={} principalId={} reason={}",
                session.sessionId(), principalId, closeReasonOf(session));
    }

    /**
     * 以指定原因码关闭会话（写 WS Close 帧后断开）。
     */
    public void close(ClientSession session, CloseReason reason) {
        Channel channel = session.getChannel();
        channel.attr(CLOSE_REASON_KEY).set(reason);
        channel.writeAndFlush(new CloseWebSocketFrame(reason.getWsCode(), reason.getPhrase()))
                .addListener(future -> channel.close());
    }

    // ==================== 查询 ====================

    /** 指定 principal 的活跃会话列表（无则空列表） */
    public List<ClientSession> findByPrincipal(String principalId) {
        return byPrincipal.getOrDefault(principalId, new CopyOnWriteArrayList<>());
    }

    /** 指定 IP 的活跃连接数 */
    public int ipConnections(String ip) {
        AtomicInteger count = byIp.get(ip);
        return count == null ? 0 : count.get();
    }

    /** 指定 principal 的活跃连接数 */
    public int principalConnections(String principalId) {
        return findByPrincipal(principalId).size();
    }

    /** 本节点全部活跃会话快照 */
    public List<ClientSession> allSessions() {
        return byPrincipal.values().stream().flatMap(List::stream).toList();
    }

    /** 本节点活跃连接总数 */
    public int activeCount() {
        return byPrincipal.values().stream().mapToInt(List::size).sum();
    }

    private CloseReason closeReasonOf(ClientSession session) {
        CloseReason reason = session.getChannel().attr(CLOSE_REASON_KEY).get();
        return reason == null ? CloseReason.CLIENT_CLOSE : reason;
    }
}
