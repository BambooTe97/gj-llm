package com.gj.llm.netty.handler;

import com.gj.llm.netty.config.GjNettyProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 鉴权失败限流器 —— 单 IP 短时间内鉴权失败达到阈值后进入冷却期，
 * 冷却期内连接直接拒绝（防 token 爆破）。护栏 #3 的机制化实现。
 *
 * @author gj-llm
 */
@Component
@ConditionalOnProperty(prefix = "gj.netty", name = "enabled", havingValue = "true")
public class AuthRateLimiter {

    /** 失败记录窗口 = 冷却时长：窗口内的失败次数达到阈值即触发冷却 */
    private final int threshold;
    private final long cooldownMillis;

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    private static final class Window {
        final Deque<Long> failureTimestamps = new ArrayDeque<>();
        volatile long cooldownUntil;
    }

    public AuthRateLimiter(GjNettyProperties properties) {
        this.threshold = properties.getLimits().getAuthFailThreshold();
        this.cooldownMillis = properties.getLimits().getAuthFailCooldownSeconds() * 1000L;
    }

    /**
     * 该 IP 是否在冷却期内。
     */
    public boolean isCoolingDown(String ip) {
        Window window = windows.get(ip);
        return window != null && System.currentTimeMillis() < window.cooldownUntil;
    }

    /**
     * 记录一次鉴权失败；窗口内累计达到阈值则启动冷却。
     */
    public void recordFailure(String ip) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(ip, k -> new Window());
        synchronized (window) {
            window.failureTimestamps.addLast(now);
            evictExpired(window, now);
            if (window.failureTimestamps.size() >= threshold) {
                window.cooldownUntil = now + cooldownMillis;
                window.failureTimestamps.clear();
            }
        }
    }

    /**
     * 鉴权成功后清除失败记录。
     */
    public void reset(String ip) {
        windows.remove(ip);
    }

    private void evictExpired(Window window, long now) {
        Iterator<Long> it = window.failureTimestamps.iterator();
        while (it.hasNext()) {
            if (now - it.next() > cooldownMillis) {
                it.remove();
            } else {
                break;
            }
        }
    }
}
