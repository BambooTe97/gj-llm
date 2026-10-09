package com.gj.llm.base.listener;

import com.gj.llm.base.entity.LogininforEntity;
import com.gj.llm.base.event.LogininforEvent;
import com.gj.llm.base.mapper.LogininforMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 登录日志事件监听器 -- @Async 异步落库，不阻塞登录主链路。
 *
 * <p>线程池复用 gj-common {@code AsyncThreadPoolConfig} 的 taskExecutor；
 * 落库失败只记 WARN 不上抛（审计是旁路，不能影响业务）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogininforEventListener {

    private final LogininforMapper logininforMapper;

    @Async
    @EventListener
    public void onLogininfor(LogininforEvent event) {
        try {
            LogininforEntity entity = LogininforEntity.builder()
                    .username(event.username())
                    .userId(event.userId())
                    .ip(event.ip())
                    .browser(event.browser())
                    .os(event.os())
                    .status(event.status())
                    .msg(event.msg())
                    .loginTime(event.loginTime())
                    .build();
            logininforMapper.insert(entity);
        } catch (Exception e) {
            log.warn("[登录日志] 落库失败: username={}, ip={}", event.username(), event.ip(), e);
        }
    }
}
