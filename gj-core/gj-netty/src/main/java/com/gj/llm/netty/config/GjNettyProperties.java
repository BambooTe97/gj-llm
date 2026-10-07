package com.gj.llm.netty.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * gj-netty 配置属性 —— 前缀 {@code gj.netty}，默认值与 {@code GJ_NETTY_GUIDE.md} 第十节一致。
 *
 * <p><b>enabled 默认 false</b>：基座能力按需激活，关闭时模块零启动（不占端口、不建线程）。</p>
 *
 * @author gj-llm
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gj.netty")
public class GjNettyProperties {

    /** 模块开关，默认关闭 */
    private boolean enabled = false;

    /** WS 监听端口 */
    private int port = 9090;

    /** WS 握手路径 */
    private String path = "/ws";

    private final Heartbeat heartbeat = new Heartbeat();
    private final Limits limits = new Limits();
    private final Dispatch dispatch = new Dispatch();
    private final Shutdown shutdown = new Shutdown();

    /** 内存泄漏检测级别（护栏 #5），dev 建议 PARANOID */
    private LeakDetectionLevel leakDetection = LeakDetectionLevel.SIMPLE;

    public enum LeakDetectionLevel {
        PARANOID, SIMPLE, DISABLED
    }

    /** 心跳与半开连接清理（护栏 #6） */
    @Getter
    @Setter
    public static class Heartbeat {
        /** 读空闲秒数：超过即断开（半开连接清理） */
        private int readIdleSeconds = 60;
        /** 写空闲秒数：触发服务端主动 ping */
        private int writeIdleSeconds = 30;
    }

    /** 护栏阈值（护栏 #1/#2/#3/#7） */
    @Getter
    @Setter
    public static class Limits {
        /** 上行帧长硬上限（字节）；下行超此值仅软告警 */
        private int maxFrameBytes = 65536;
        /** 单 IP 连接上限——内网 NAT / 反代场景按部署形态调大（GUIDE 第七节坑位说明） */
        private int maxConnectionsPerIp = 128;
        /** 单 principal 连接上限（主闸门，不受拓扑影响） */
        private int maxConnectionsPerPrincipal = 8;
        /** 鉴权失败阈值：窗口内达到即进入冷却 */
        private int authFailThreshold = 5;
        /** 鉴权失败冷却时长（秒） */
        private int authFailCooldownSeconds = 300;
        /** 每连接上行速率上限（条/秒），超速丢弃计数 */
        private int inboundRatePerConn = 100;
    }

    /** 派发层（每会话顺序队列，见 GUIDE 第八节） */
    @Getter
    @Setter
    public static class Dispatch {
        /** 派发模式：virtual=每会话顺序虚拟线程(默认) | pool=共享池drain兜底 | direct=内联(仅测试) */
        private DispatchMode mode = DispatchMode.VIRTUAL;
        /** 每会话派发队列容量：溢出 = 慢消费者断开（护栏联动） */
        private int perSessionQueue = 256;
        /** pool 模式共享执行器核心线程数 */
        private int poolCoreSize = 8;
        /** pool 模式共享执行器最大线程数 */
        private int poolMaxSize = 32;
    }

    public enum DispatchMode {
        VIRTUAL, POOL, DIRECT
    }

    /** 优雅停机 */
    @Getter
    @Setter
    public static class Shutdown {
        /** 停新连接 → 通知客户端 → drain 等待秒数 */
        private int graceSeconds = 10;
    }
}
