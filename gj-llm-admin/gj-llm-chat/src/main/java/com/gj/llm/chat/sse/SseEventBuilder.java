package com.gj.llm.chat.sse;

import com.gj.llm.common.util.JacksonUtils;
import org.springframework.http.codec.ServerSentEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSE 事件构建工具 -- 统一所有智能体输出的 SSE 协议。
 *
 * <p>协议事件类型:thinking / references / content / no_result / error / done。
 * 前端按 {@code type} 字段分发,故除 done 外的事件都用 {@link #event(String, Object)} 带上 type。</p>
 *
 * @author gj-llm
 */
public final class SseEventBuilder {

    private SseEventBuilder() {
    }

    /**
     * 构建带 type 的 SSE 事件。
     *
     * @param type 事件类型(thinking/references/content/no_result/error)
     * @param data 事件数据(POJO 或 Map),会被序列化进 payload
     */
    public static ServerSentEvent<String> event(String type, Object data) {
        Map<String, Object> payload = new LinkedHashMap<>(JacksonUtils.toMap(data));
        payload.put("type", type);
        return ServerSentEvent.builder(JacksonUtils.toJson(payload)).build();
    }

    /**
     * 构建无 type 的原始 SSE 事件(done 事件用 -- payload 自带 messageId 等字段)。
     * 保持与原实现一致:done 事件不带 type,前端依靠流结束信号收尾。
     */
    public static ServerSentEvent<String> raw(Map<String, Object> payload) {
        return ServerSentEvent.builder(JacksonUtils.toJson(payload)).build();
    }
}
