package com.gj.llm.netty.protocol;

/**
 * 协议消息不合法 —— 帧长超限之外的解码失败（版本不符、topic 命名非法、JSON 解析失败等）。
 * 由 {@link MessageCodec#decode} 抛出，解码 handler 捕获后计数并丢弃，不关闭连接。
 *
 * @author gj-llm
 */
public class InvalidMessageException extends RuntimeException {

    public InvalidMessageException(String message) {
        super(message);
    }

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
