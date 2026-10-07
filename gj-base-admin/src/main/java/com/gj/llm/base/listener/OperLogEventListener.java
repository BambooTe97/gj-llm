package com.gj.llm.base.listener;

import com.gj.llm.base.entity.OperLogEntity;
import com.gj.llm.base.event.OperLogEvent;
import com.gj.llm.base.mapper.OperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 操作日志事件监听器 -- @Async 异步落库，不阻塞业务主链路。
 *
 * <p>线程池复用 gj-common {@code AsyncThreadPoolConfig} 的 taskExecutor；
 * 落库失败只记 WARN 不上抛（审计是旁路，不能影响业务）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperLogEventListener {

    private final OperLogMapper operLogMapper;

    @Async
    @EventListener
    public void onOperLog(OperLogEvent event) {
        try {
            OperLogEntity entity = OperLogEntity.builder()
                    .module(event.module())
                    .type(event.type())
                    .method(event.method())
                    .requestUri(event.requestUri())
                    .requestMethod(event.requestMethod())
                    .operator(event.operator())
                    .userId(event.userId())
                    .ip(event.ip())
                    .params(event.params())
                    .result(event.result())
                    .status(event.status())
                    .errorMsg(event.errorMsg())
                    .costMs(event.costMs())
                    .createdAt(LocalDateTime.now())
                    .build();
            operLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("[操作日志] 落库失败: module={}, type={}, method={}",
                    event.module(), event.type(), event.method(), e);
        }
    }
}
