package com.gj.llm.netty.spi;

import io.netty.channel.ChannelPipeline;

/**
 * pipeline 定制 SPI —— 向传输链路追加 handler（压缩、限速、埋点等），不动核心装配。
 *
 * <p>使用方式：实现类声明关心的 {@link PipelinePhase}，在对应挂载点用
 * {@code pipeline.addAfter(阶段锚点名, "业务handler名", handler)} 插入；
 * 各阶段锚点名见 {@code HandlerNames} 常量。接入的 handler 若有阻塞行为，
 * 必须自行转到业务线程，禁止阻塞 EventLoop（接入文档须明示）。</p>
 *
 * @author gj-llm
 */
public interface PipelineCustomizer {

    /**
     * 在指定挂载阶段追加 handler。每个连接建立时调用一次。
     */
    void customize(ChannelPipeline pipeline, PipelinePhase phase);
}
