package com.gj.llm.netty.spi;

/**
 * pipeline 挂载阶段 —— {@link PipelineCustomizer} 的插入位置约定。
 *
 * @author gj-llm
 */
public enum PipelinePhase {

    /**
     * 握手阶段（HTTP 层，WebSocketServerProtocolHandler 之前），
     * 锚点 {@code HandlerNames.HANDSHAKE_AUTH}。
     */
    HANDSHAKE,

    /**
     * 编解码阶段（信封 Decoder 之后），锚点 {@code HandlerNames.MESSAGE_ENCODER}。
     */
    CODEC,

    /**
     * 派发阶段（TopicDispatchHandler 之前），锚点 {@code HandlerNames.TOPIC_DISPATCH}。
     */
    DISPATCH
}
