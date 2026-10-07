package com.gj.llm.netty.spi;

import com.gj.llm.netty.protocol.CloseReason;
import com.gj.llm.netty.session.ClientSession;

/**
 * 连接生命周期钩子 SPI —— 上下线事件通知（在线状态维护、资源清理等）。
 *
 * <p>回调在 Netty EventLoop 上执行，<b>禁止阻塞</b>；重活请自行转派发层。
 * 实现 bean 由基座自动收集，无需手工注册。</p>
 *
 * @author gj-llm
 */
public interface ConnectionLifecycleListener {

    /**
     * 连接建立（握手鉴权通过并注册成功）后回调。
     */
    default void onConnected(ClientSession session) {
    }

    /**
     * 连接断开后回调，携带断开原因码。
     */
    default void onDisconnected(ClientSession session, CloseReason reason) {
    }
}
