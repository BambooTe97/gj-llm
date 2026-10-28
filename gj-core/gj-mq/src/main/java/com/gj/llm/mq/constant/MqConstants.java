package com.gj.llm.mq.constant;

/**
 * gj-mq 常量 —— 消息头命名与内容类型约定（各适配器共用，保证跨适配器信封行为一致）。
 *
 * <p>常量放接口/常量类上，遵循项目分层规范（{@code constant/} 包 + final 类 + 私有构造）。</p>
 *
 * @author gj-llm
 */
public final class MqConstants {

    private MqConstants() {
    }

    // ==================== 消息头 ====================

    /** 消息 ID 头（传输层冗余携带，便于在 broker 控制台/日志中检索） */
    public static final String HEADER_MESSAGE_ID = "gj-message-id";

    /** 业务主题头（Kafka 无 tag 概念，tag 携带于消息头） */
    public static final String HEADER_TAG = "gj-tag";

    /** 消息内容类型 */
    public static final String CONTENT_TYPE_JSON = "application/json";

    // ==================== topic 命名 ====================

    /** topic 命名约定：小写字母/数字/点/下划线/中划线（RocketMQ 与 Kafka 的公共子集），如 notify.message */
    public static final String TOPIC_PATTERN = "[a-zA-Z0-9._-]{1,200}";

    /** 校验 topic 命名（两个适配器对 topic 字符集要求不同，取公共子集防御性校验） */
    public static boolean isValidTopic(String topic) {
        return topic != null && topic.matches(TOPIC_PATTERN);
    }
}
