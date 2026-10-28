package com.gj.llm.mq.spi;

/**
 * 适配器能力声明 —— API 能力降级的诚实声明：统一门面按此判定是直接转发还是抛
 * {@link UnsupportedOperationException}，避免业务在错误假设上堆代码。
 *
 * @author gj-llm
 */
public record MqCapability(boolean supportsDelay, boolean supportsOrdering, boolean supportsTransaction) {

    public static MqCapability of(boolean delay, boolean ordering, boolean transaction) {
        return new MqCapability(delay, ordering, transaction);
    }
}
