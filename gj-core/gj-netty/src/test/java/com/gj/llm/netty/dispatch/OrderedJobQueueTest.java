package com.gj.llm.netty.dispatch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 顺序派发队列单测 —— 顺序性、有界性（慢消费者信号）、三种模式。
 */
class OrderedJobQueueTest {

    private static final Consumer<Throwable> NO_ERROR = t -> {
        throw new AssertionError("不应有任务异常: " + t, t);
    };

    @Test
    void virtualModePreservesOrder() throws InterruptedException {
        OrderedJobQueue queue = OrderedJobQueue.virtual(1000, NO_ERROR);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(200);
        for (int i = 0; i < 200; i++) {
            int seq = i;
            assertTrue(queue.enqueue(() -> {
                order.add(seq);
                done.countDown();
            }));
        }
        assertTrue(done.await(5, TimeUnit.SECONDS), "任务未在超时内执行完");
        for (int i = 0; i < 200; i++) {
            assertEquals(i, order.get(i), "虚拟线程顺序派发必须保序");
        }
        queue.close();
    }

    @Test
    void poolModePreservesOrder() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        OrderedJobQueue queue = OrderedJobQueue.pooled(1000, executor, NO_ERROR);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            int seq = i;
            assertTrue(queue.enqueue(() -> {
                order.add(seq);
                done.countDown();
            }));
        }
        assertTrue(done.await(5, TimeUnit.SECONDS), "任务未在超时内执行完");
        for (int i = 0; i < 100; i++) {
            assertEquals(i, order.get(i), "pool drain 模式必须保序");
        }
        queue.close();
        executor.shutdown();
    }

    @Test
    void boundedQueueSignalsOverflow() throws InterruptedException {
        OrderedJobQueue queue = OrderedJobQueue.virtual(2, NO_ERROR);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch firstRunning = new CountDownLatch(1);

        // 任务 1 占住消费者
        assertTrue(queue.enqueue(() -> {
            firstRunning.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(firstRunning.await(5, TimeUnit.SECONDS), "首个任务未开始执行");

        // 填满队列（容量 2），后续入队必须返回 false（慢消费者信号）
        boolean overflow = false;
        for (int i = 0; i < 10; i++) {
            if (!queue.enqueue(() -> {})) {
                overflow = true;
                break;
            }
        }
        assertTrue(overflow, "队列满后 enqueue 必须返回 false");
        assertEquals(2, queue.pendingCount());

        release.countDown();
        queue.close();
    }

    @Test
    void closedQueueRejectsTasks() {
        OrderedJobQueue queue = OrderedJobQueue.virtual(10, NO_ERROR);
        queue.close();
        assertFalse(queue.enqueue(() -> {}));
    }

    @Test
    void taskErrorDoesNotKillWorker() throws InterruptedException {
        AtomicReference<Throwable> caught = new AtomicReference<>();
        OrderedJobQueue queue = OrderedJobQueue.virtual(10, caught::set);
        CountDownLatch done = new CountDownLatch(1);
        queue.enqueue(() -> { throw new IllegalStateException("boom"); });
        queue.enqueue(done::countDown); // 异常任务之后的任务仍须执行
        assertTrue(done.await(5, TimeUnit.SECONDS), "任务异常不应杀死派发线程");

        // 有界轮询等待错误回调（不用 Awaitility，starter-test 未包含）
        long deadline = System.currentTimeMillis() + 5000;
        while (caught.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals("boom", caught.get().getMessage());
        queue.close();
    }

    @Test
    void directModeRunsInline() {
        AtomicBoolean ran = new AtomicBoolean(false);
        OrderedJobQueue queue = OrderedJobQueue.direct(NO_ERROR);
        assertTrue(queue.enqueue(() -> ran.set(true)));
        assertTrue(ran.get(), "direct 模式必须内联执行");
        queue.close();
    }
}
