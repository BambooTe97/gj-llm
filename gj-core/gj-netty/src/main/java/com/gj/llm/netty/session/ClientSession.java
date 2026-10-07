package com.gj.llm.netty.session;

import com.gj.llm.netty.dispatch.OrderedJobQueue;
import com.gj.llm.netty.protocol.MessageEnvelope;
import com.gj.llm.netty.spi.ClientPrincipal;
import io.netty.channel.Channel;
import lombok.Getter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 客户端会话 —— 一条已通过握手鉴权的连接在基座内的业务视图：
 * 身份（{@link ClientPrincipal}）+ 通道（{@link Channel}）+ 会话属性 + 专属派发队列。
 *
 * <p>会话属性供业务打标（如租户、设备号），随连接生命周期存亡。</p>
 *
 * @author gj-llm
 */
@Getter
public final class ClientSession {

    /** 底层 Netty 通道 */
    private final Channel channel;

    /** 握手鉴权产出的身份（默认拒绝模式下不会出现无 principal 的会话） */
    private final ClientPrincipal principal;

    /** 连接建立时间（毫秒） */
    private final long connectedAt = System.currentTimeMillis();

    /** 会话属性（业务打标，如 tenantId、deviceType） */
    private final ConcurrentMap<String, String> attributes = new ConcurrentHashMap<>();

    /** 会话专属顺序派发队列（每会话一个虚拟线程消费），由 {@code DispatchService} 创建 */
    private final OrderedJobQueue jobQueue;

    public ClientSession(Channel channel, ClientPrincipal principal, OrderedJobQueue jobQueue) {
        this.channel = channel;
        this.principal = principal;
        this.jobQueue = jobQueue;
    }

    /** 身份唯一标识 */
    public String principalId() {
        return principal.id();
    }

    /** 连接唯一标识 */
    public String sessionId() {
        return channel.id().asLongText();
    }

    /** 连接是否活跃 */
    public boolean isActive() {
        return channel.isActive();
    }

    /**
     * 向客户端推送一条消息（写入出站 pipeline，由 MessageEncoder 编码为文本帧）。
     */
    public void send(MessageEnvelope envelope) {
        channel.writeAndFlush(envelope);
    }
}
