package com.gj.llm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * gj-llm 应用入口。
 *
 * @author gj-llm
 */
@EnableAsync
@SpringBootApplication
public class GjLlmApplication {

    public static void main(String[] args) {
        SpringApplication.run(GjLlmApplication.class, args);
    }

    /**
     * 异步任务线程池 —— 替换默认的 SimpleAsyncTaskExecutor，
     * 避免无限制创建线程导致资源耗尽。
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
