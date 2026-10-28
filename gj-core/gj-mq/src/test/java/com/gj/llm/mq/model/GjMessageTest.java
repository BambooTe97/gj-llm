package com.gj.llm.mq.model;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.mq.TestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一信封序列化/负载转换单测。
 */
class GjMessageTest {

    /** 简单业务负载（record，Jackson 3 原生支持） */
    record DemoPayload(String name, int count) {
    }

    @BeforeAll
    static void initJson() {
        TestSupport.initJackson();
    }

    @Test
    void ofGeneratesIdAndTimestamp() {
        GjMessage message = GjMessage.of("notify.message", "created", Map.of("k", "v"));

        assertNotNull(message.getMessageId());
        assertEquals(36, message.getMessageId().length());
        assertTrue(message.getTimestamp() > 0);
        assertEquals(0, message.getRetryCount());
    }

    @Test
    void envelopeRoundtripKeepsPayloadStructure() {
        GjMessage source = GjMessage.of("rag.document.ingest", "completed",
                Map.of("fileId", "123", "nested", Map.of("step", 2)));
        source.getHeaders().put("traceId", "t-001");

        String json = JacksonUtils.toJson(source);
        GjMessage decoded = JacksonUtils.fromJson(json, GjMessage.class);

        assertEquals(source.getMessageId(), decoded.getMessageId());
        assertEquals(source.getTopic(), decoded.getTopic());
        assertEquals(source.getTag(), decoded.getTag());
        assertEquals("t-001", decoded.getHeaders().get("traceId"));
        // 负载嵌套 JSON，无二次编码
        Object payload = decoded.getPayload();
        assertInstanceOf(Map.class, payload);
        assertEquals("123", ((Map<?, ?>) payload).get("fileId"));
        assertInstanceOf(Map.class, ((Map<?, ?>) payload).get("nested"));
    }

    @Test
    void payloadAsFromMap() {
        GjMessage message = GjMessage.of("t", null, Map.of("name", "文档A", "count", 3));

        DemoPayload payload = message.payloadAs(DemoPayload.class);

        assertEquals("文档A", payload.name());
        assertEquals(3, payload.count());
    }

    @Test
    void payloadAsFromJsonString() {
        GjMessage message = GjMessage.of("t", null, "{\"name\":\"文档B\",\"count\":5}");

        DemoPayload payload = message.payloadAs(DemoPayload.class);

        assertEquals("文档B", payload.name());
        assertEquals(5, payload.count());
    }

    @Test
    void payloadAsIdempotentAndNullSafe() {
        DemoPayload origin = new DemoPayload("已转换", 1);
        GjMessage message = GjMessage.of("t", null, origin);

        assertEquals(origin, message.payloadAs(DemoPayload.class));
        assertNull(GjMessage.of("t", null, null).payloadAs(DemoPayload.class));
    }
}
