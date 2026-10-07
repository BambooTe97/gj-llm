package com.gj.llm.netty.dispatch;

import lombok.extern.slf4j.Slf4j;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 会话专属顺序任务队列 —— 线程模型的核心构件，规则见 {@code GJ_NETTY_GUIDE.md} 第八节：
 *
 * <ul>
 *   <li><b>顺序性</b>：同一队列内的任务严格按入队顺序执行（同会话消息顺序天然保住）</li>
 *   <li><b>有界</b>：队列满时 {@link #enqueue} 返回 false（= 慢消费者，由调用方断开）</li>
 *   <li><b>三种执行模式</b>：
 *     <ul>
 *       <li>{@link #virtual}：每队列一个专用虚拟线程阻塞消费（默认，JDK 25 Loom）</li>
 *       <li>{@link #pooled}：共享线程池 + drain-to-batch（同会话同一时刻只有一个 drain 任务在跑，保序）；
 *           兜底模式，防未来接入 JNI 类驱动出现 pinning</li>
 *       <li>{@link #direct}：调用线程内联执行，仅用于测试</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author gj-llm
 */
@Slf4j
public final class OrderedJobQueue {

    private final Queue<Runnable> queue;
    private final int capacity;
    private final Executor worker;
    private final Consumer<Throwable> errorConsumer;
    private final boolean virtualMode;
    private final boolean directMode;

    /** pool 模式防重入标记：同一时刻至多一个 drain 任务在消费本队列 */
    private final AtomicBoolean draining = new AtomicBoolean();

    private volatile boolean closed;
    private volatile Thread virtualWorker;

    private OrderedJobQueue(Queue<Runnable> queue, int capacity, Executor worker,
                            Consumer<Throwable> errorConsumer, boolean virtualMode, boolean directMode) {
        this.queue = queue;
        this.capacity = capacity;
        this.worker = worker;
        this.errorConsumer = errorConsumer;
        this.virtualMode = virtualMode;
        this.directMode = directMode;
    }

    /**
     * 虚拟线程模式：每队列一个专用虚拟线程阻塞消费（默认）。
     */
    public static OrderedJobQueue virtual(int capacity, Consumer<Throwable> errorConsumer) {
        OrderedJobQueue q = new OrderedJobQueue(
                new ArrayBlockingQueue<>(capacity), capacity, null, errorConsumer, true, false);
        q.startVirtualWorker();
        return q;
    }

    /**
     * 共享线程池模式：drain-to-batch 消费（兜底）。
     */
    public static OrderedJobQueue pooled(int capacity, Executor worker, Consumer<Throwable> errorConsumer) {
        return new OrderedJobQueue(
                new LinkedBlockingQueue<>(capacity), capacity, worker, errorConsumer, false, false);
    }

    /**
     * 内联执行模式：调用线程直接执行，仅用于测试。
     */
    public static OrderedJobQueue direct(Consumer<Throwable> errorConsumer) {
        return new OrderedJobQueue(null, 0, null, errorConsumer, false, true);
    }

    /**
     * 入队一个任务。
     *
     * @return false 表示队列已满或已关闭（= 慢消费者，调用方应断开会话）
     */
    public boolean enqueue(Runnable task) {
        if (closed) {
            return false;
        }
        if (directMode) {
            runGuarded(task);
            return true;
        }
        if (!queue.offer(task)) {
            return false;
        }
        if (!virtualMode) {
            scheduleDrain();
        }
        return true;
    }

    /** 队列当前积压任务数（指标用） */
    public int pendingCount() {
        return queue == null ? 0 : queue.size();
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * 关闭队列（会话注销时调用）：停止接收新任务；虚拟线程被中断退出，pool 模式自然排空。
     */
    public void close() {
        closed = true;
        Thread worker = virtualWorker;
        if (worker != null) {
            worker.interrupt();
        }
    }

    // ==================== 内部实现 ====================

    private void startVirtualWorker() {
        Thread thread = Thread.ofVirtual().name("gj-netty-dispatch-v").start(() -> {
            while (!closed) {
                try {
                    Runnable task = ((ArrayBlockingQueue<Runnable>) queue).take();
                    runGuarded(task);
                } catch (InterruptedException e) {
                    break; // close() 中断退出
                }
            }
        });
        this.virtualWorker = thread;
    }

    private void scheduleDrain() {
        if (draining.compareAndSet(false, true)) {
            worker.execute(this::drainLoop);
        }
    }

    private void drainLoop() {
        try {
            while (!closed) {
                Runnable task = queue.poll();
                if (task == null) {
                    if (queue.isEmpty()) {
                        return;
                    }
                    continue;
                }
                runGuarded(task);
            }
        } finally {
            draining.set(false);
            // 与 scheduleDrain 之间存在窄窗竞态：排空期间可能有新任务入队，补一次调度
            if (!queue.isEmpty() && !closed) {
                scheduleDrain();
            }
        }
    }

    private void runGuarded(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            log.error("gj-netty 派发任务执行异常", t);
            errorConsumer.accept(t);
        }
    }

    /** 供 {@link DispatchService} 创建 pool 模式共享执行器的默认工厂。 */
    static ThreadPoolExecutor newPoolExecutor(int coreSize, int maxSize) {
        return new ThreadPoolExecutor(coreSize, maxSize, 60L, java.util.concurrent.TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                Thread.ofVirtual().name("gj-netty-dispatch-", 0).factory());
    }
}
