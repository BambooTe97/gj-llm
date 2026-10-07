package com.gj.llm.netty.session;

/**
 * 连接数配额超限 —— 单 IP 或单 principal 的活跃连接数达到上限时由
 * {@link SessionRegistry#register} 抛出，连接以 {@code QUOTA_EXCEEDED} 原因码关闭。
 *
 * @author gj-llm
 */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String message) {
        super(message);
    }
}
