package com.gj.llm.mq.model;

import com.gj.llm.common.util.JacksonUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一消息信封 —— 业务与 MQ 实现之间的唯一契约：无论 RocketMQ 还是 Kafka，
 * 线上传输的都是本信封的 JSON 序列化结果（payload 以嵌套 JSON 内联，无二次编码）。
 *
 * <p>序列化统一走 {@link JacksonUtils}（与全局 Long→String、日期序列化行为一致，不自持 mapper）。</p>
 *
 * @author gj-llm
 */
@Getter
@Setter
public class GjMessage {

    /**
     * 消息 ID（UUID），生产端生成，全链路日志检索键
     */
    private String messageId;

    /**
     * 业务主题，如 notify.message、rag.document.ingest
     */
    private String topic;

    /**
     * 子类型/事件名，可空。RocketMQ 映射为 tag，Kafka 携带于 {@code gj-tag} 消息头
     */
    private String tag;

    /**
     * 扩展头（透传给传输层），如 traceId
     */
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * 业务负载（嵌套 JSON）。消费端经 {@link #payloadAs(Class)} 转换为目标类型
     */
    private Object payload;

    /**
     * 发送时间戳（毫秒）
     */
    private long timestamp;

    /**
     * 消费侧重试次数（适配器回填：RocketMQ 取 reconsumeTimes，Kafka 取本地重试计数），仅供观察
     */
    private int retryCount;

    // ==================== 工厂 ====================

    public static GjMessage of(String topic, String tag, Object payload) {
        GjMessage message = new GjMessage();
        message.messageId = UUID.randomUUID().toString();
        message.topic = topic;
        message.tag = tag;
        message.payload = payload;
        message.timestamp = System.currentTimeMillis();
        return message;
    }

    // ==================== 负载转换 ====================

    /**
     * 负载转换：Map → 目标类型（fromMap）；String → fromJson；其余先序列化再反序列化。
     * 目标类型已是实例时直接返回（幂等）。
     */
    @SuppressWarnings("unchecked")
    public <T> T payloadAs(Class<T> type) {
        Object p = this.payload;
        if (p == null) {
            return null;
        }
        if (type.isInstance(p)) {
            return (T) p;
        }
        if (p instanceof Map<?, ?> map) {
            return JacksonUtils.fromMap((Map<String, Object>) map, type);
        }
        if (p instanceof String s) {
            return JacksonUtils.fromJson(s, type);
        }
        return JacksonUtils.fromJson(JacksonUtils.toJson(p), type);
    }
}
