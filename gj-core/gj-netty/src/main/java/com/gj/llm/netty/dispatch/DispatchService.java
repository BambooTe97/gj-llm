package com.gj.llm.netty.dispatch;

import com.gj.llm.netty.config.GjNettyProperties;
import com.gj.llm.netty.metrics.NettyMetrics;
import com.gj.llm.netty.session.ClientSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 派发服务 —— 管理会话专属的 {@link OrderedJobQueue} 生命周期，向上提供统一入口
 * {@link #submit}。业务代码只会运行在派发层（虚拟线程 / pool 兜底），
 * EventLoop 上永不执行业务逻辑——线程模型纪律的机制化保障。
 *
 * <p>派发层全局在途任务数通过 {@link #inFlightCount()} 暴露为指标
 * {@code gj.netty.dispatch.queue.size}。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "gj.netty", name = "enabled", havingValue = "true")
public class DispatchService {

    private final GjNettyProperties properties;
    private final NettyMetrics metrics;

    /** pool 模式共享执行器（懒创建），virtual/direct 模式为 null */
    private volatile ThreadPoolExecutor poolExecutor;

    /** 全局在途任务数（含队列中），对应派发队列深度指标 */
    private final LongAdder inFlight = new LongAdder();

    /** 活跃队列数（会话数维度观测） */
    private final AtomicInteger activeQueues = new AtomicInteger();

    public DispatchService(GjNettyProperties properties, NettyMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * 为新会话创建顺序派发队列。
     */
    public OrderedJobQueue newQueue() {
        activeQueues.incrementAndGet();
        return switch (properties.getDispatch().getMode()) {
            case VIRTUAL -> OrderedJobQueue.virtual(
                    properties.getDispatch().getPerSessionQueue(), this::onTaskError);
            case POOL -> OrderedJobQueue.pooled(
                    properties.getDispatch().getPerSessionQueue(),
                    poolExecutor(), this::onTaskError);
            case DIRECT -> OrderedJobQueue.direct(this::onTaskError);
        };
    }

    /**
     * 向会话派发一个任务（顺序保序）。
     *
     * @return false 表示队列已满（慢消费者）或会话已关闭——调用方应以
     *         {@code SLOW_CONSUMER} 断开会话
     */
    public boolean submit(ClientSession session, Runnable task) {
        OrderedJobQueue queue = session.getJobQueue();
        if (queue == null || queue.isClosed()) {
            return false;
        }
        inFlight.increment();
        boolean enqueued = queue.enqueue(() -> {
            try {
                task.run();
            } finally {
                inFlight.decrement();
            }
        });
        if (!enqueued) {
            inFlight.decrement(); // 未入队，回滚计数
        }
        return enqueued;
    }

    /**
     * 注销会话队列（连接断开时调用）。
     */
    public void closeQueue(OrderedJobQueue queue) {
        if (queue != null) {
            queue.close();
            activeQueues.decrementAndGet();
        }
    }

    /** 派发层全局在途任务数（指标 {@code gj.netty.dispatch.queue.size}） */
    public long inFlightCount() {
        return inFlight.sum();
    }

    /** 活跃队列数（约等于活跃会话数） */
    public int activeQueueCount() {
        return activeQueues.get();
    }

    /**
     * 服务停机时释放 pool 模式执行器（virtual 模式无共享资源）。
     */
    public void shutdown() {
        ThreadPoolExecutor executor = poolExecutor;
        if (executor != null) {
            executor.shutdown();
        }
    }

    private ThreadPoolExecutor poolExecutor() {
        ThreadPoolExecutor executor = this.poolExecutor;
        if (executor == null) {
            synchronized (this) {
                if (poolExecutor == null) {
                    poolExecutor = OrderedJobQueue.newPoolExecutor(
                            properties.getDispatch().getPoolCoreSize(),
                            properties.getDispatch().getPoolMaxSize());
                }
                executor = poolExecutor;
            }
        }
        return executor;
    }

    private void onTaskError(Throwable t) {
        metrics.exception(t);
    }
}
