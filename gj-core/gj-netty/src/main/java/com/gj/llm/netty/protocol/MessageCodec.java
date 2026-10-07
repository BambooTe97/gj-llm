package com.gj.llm.netty.protocol;

import com.gj.llm.common.exception.UtilException;
import com.gj.llm.common.util.JacksonUtils;
import tools.jackson.databind.JsonNode;

/**
 * 协议信封编解码器 —— JSON 行为统一走 gj-common 的 {@link JacksonUtils}：
 * 生产环境经 {@code SpringUtils} 取 Spring 容器的 JsonMapper（JacksonConfig
 * 统一增强：Long→String、日期格式等），与项目其它模块完全一致，
 * 不自持 mapper、不出现第二种 JSON 转换行为。
 *
 * <p>纯单测环境（无 Spring 上下文）由测试侧向 {@code JacksonUtils} 的静态
 * mapper 反射注入（项目既定做法，见 test 包 {@code TestSupport}）。</p>
 *
 * @author gj-llm
 */
public final class MessageCodec {

    private MessageCodec() {
    }

    /**
     * 编码信封为 JSON 字符串。payload 为任意可序列化对象。
     *
     * @throws com.gj.llm.common.exception.UtilException payload 不可序列化时
     */
    public static String encode(MessageEnvelope envelope) {
        return JacksonUtils.toJson(envelope);
    }

    /**
     * 解码 JSON 为信封。
     *
     * <p>校验：版本必须为 {@link MessageEnvelope#CURRENT_VERSION}；type 必须是合法 topic
     * （{@link Topic#isValid}）；payload 保持为 {@link JsonNode}。
     * 数值字段经 {@code asLong/asInt} 读取，天然兼容全局 Long→String 配置下
     * 的字符串形态数字。</p>
     *
     * @throws InvalidMessageException JSON 非法、版本不符或 topic 命名非法
     */
    public static MessageEnvelope decode(String json) {
        JsonNode root;
        try {
            root = JacksonUtils.readTree(json);
        } catch (UtilException e) {
            throw new InvalidMessageException("JSON 解析失败", e);
        }
        if (root == null || !root.isObject()) {
            throw new InvalidMessageException("信封必须是 JSON 对象");
        }
        int v = root.path("v").asInt(-1);
        if (v != MessageEnvelope.CURRENT_VERSION) {
            throw new InvalidMessageException("不支持的协议版本: " + v);
        }
        String type = root.path("type").asText("");
        if (!Topic.isValid(type)) {
            throw new InvalidMessageException("topic 命名非法: " + type);
        }
        long seq = root.path("seq").asLong(0);
        boolean ack = root.path("ack").asBoolean(false);
        JsonNode payload = root.hasNonNull("payload") ? root.get("payload") : null;
        long ts = root.path("ts").asLong(System.currentTimeMillis());
        return new MessageEnvelope(v, type, seq, ack, payload, ts);
    }
}
