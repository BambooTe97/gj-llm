package com.gj.llm.netty.spi;

import com.gj.llm.netty.protocol.MessageEnvelope;
import com.gj.llm.netty.session.ClientSession;

/**
 * 上行消息监听器 SPI —— 业务按 topic 注册，消费客户端发来的消息。
 *
 * <p>调用时机：在会话专属的顺序派发队列上执行（每会话一个虚拟线程），
 * <b>同会话内严格按到达顺序调用</b>；拿不到 Netty EventLoop，可安全执行阻塞调用
 * （MyBatis/JDBC 等）。线程模型见 {@code GJ_NETTY_GUIDE.md} 第八节。</p>
 *
 * <p>topic 支持：
 * <ul>
 *   <li>精确匹配：{@code "chat.content"}</li>
 *   <li>前缀通配：{@code "chat.*"} —— 匹配所有以 {@code "chat."} 开头的 topic</li>
 * </ul>
 * topic 必须使用业务自己的前缀，{@code sys.*} 为基座保留。</p>
 *
 * @author gj-llm
 */
public interface MessageListener {

    /**
     * 声明关心的 topic（精确或前缀通配）。
     */
    String topic();

    /**
     * 处理上行消息。抛出的异常由派发层捕获并计入异常指标，不影响连接存活。
     */
    void onMessage(ClientSession session, MessageEnvelope message);
}
