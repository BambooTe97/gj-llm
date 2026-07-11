package com.gj.llm.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务线程池配置。
 *
 * <p>替换默认的 SimpleAsyncTaskExecutor，避免无限制创建线程导致资源耗尽。</p>
 *
 * @author gj-llm
 */
@Configuration
@EnableAsync
public class AsyncThreadPoolConfig {

    /**
     * 通用异步任务线程池（供 {@code @Async} 注解使用）。
     *
     * <ul>
     *   <li>核心线程数：2</li>
     *   <li>最大线程数：4</li>
     *   <li>队列容量：50</li>
     *   <li>线程名前缀：async-</li>
     * </ul>
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
