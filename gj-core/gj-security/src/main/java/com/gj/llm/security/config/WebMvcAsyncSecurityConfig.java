package com.gj.llm.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 异步请求 + SecurityContext 传递配置。
 *
 * <p>解决流式 SSE 接口（返回 {@code Flux} 的 Controller）在异步线程切换后
 * {@link org.springframework.security.core.context.SecurityContext} 丢失，
 * 导致 {@code AuthorizationDeniedException} 的问题。</p>
 *
 * <h3>原理</h3>
 * <p>Controller 返回 {@code Flux<ServerSentEvent>} 时，Spring MVC 会将响应写入
 * 委托给 {@link AsyncTaskExecutor} 执行。默认的 {@code SimpleAsyncTaskExecutor}
 * 不传递 {@code SecurityContext}，导致异步线程中认证信息丢失。
 * {@link DelegatingSecurityContextAsyncTaskExecutor} 在提交任务时自动将主线程的
 * {@code SecurityContext} 复制到异步线程。</p>
 *
 * @author gj-llm
 */
@Configuration
public class WebMvcAsyncSecurityConfig implements WebMvcConfigurer {

    /**
     * 构造 Spring MVC 异步请求专用的线程池，并包装 SecurityContext 传递能力。
     */
    private AsyncTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        // 关键：包装为可传递 SecurityContext 的执行器
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor());
        configurer.setDefaultTimeout(60_000);
    }
}
