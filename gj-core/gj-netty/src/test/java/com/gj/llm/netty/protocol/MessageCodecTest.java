package com.gj.llm.netty.protocol;

import com.gj.llm.netty.TestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 协议信封编解码单测。
 */
class MessageCodecTest {

    @BeforeAll
    static void initJson() {
        TestSupport.initJackson();
    }

    @Test
    void roundtrip() {
        MessageEnvelope source = MessageEnvelope.of("chat.content", Map.of("delta", "你好"));
        MessageEnvelope decoded = MessageCodec.decode(MessageCodec.encode(source));

        assertEquals(MessageEnvelope.CURRENT_VERSION, decoded.v());
        assertEquals("chat.content", decoded.type());
        assertEquals(0, decoded.seq());
        assertFalse(decoded.ack());
        assertInstanceOf(JsonNode.class, decoded.payload());
        assertEquals("你好", ((JsonNode) decoded.payload()).path("delta").asText());
    }

    @Test
    void roundtripWithAllFields() {
        MessageEnvelope source = new MessageEnvelope(1, "task.progress", 42, true, Map.of("pct", 50), 1765000000000L);
        MessageEnvelope decoded = MessageCodec.decode(MessageCodec.encode(source));

        assertEquals(42, decoded.seq());
        assertTrue(decoded.ack());
        assertEquals(1765000000000L, decoded.ts());
        assertEquals(50, ((JsonNode) decoded.payload()).path("pct").asInt());
    }

    @Test
    void missingPayloadDecodesToNull() {
        String json = "{\"v\":1,\"type\":\"sys.heartbeat\",\"seq\":0}";
        MessageEnvelope decoded = MessageCodec.decode(json);
        assertNull(decoded.payload());
    }

    @Test
    void rejectsWrongVersion() {
        String json = "{\"v\":2,\"type\":\"chat.content\"}";
        assertThrows(InvalidMessageException.class, () -> MessageCodec.decode(json));
    }

    @Test
    void rejectsIllegalTopic() {
        assertThrows(InvalidMessageException.class,
                () -> MessageCodec.decode("{\"v\":1,\"type\":\"NODOT\"}"));
        assertThrows(InvalidMessageException.class,
                () -> MessageCodec.decode("{\"v\":1,\"type\":\"nodot\"}"));
        assertThrows(InvalidMessageException.class,
                () -> MessageCodec.decode("{\"v\":1,\"type\":\".leading.dot\"}"));
    }

    @Test
    void rejectsGarbageJson() {
        assertThrows(InvalidMessageException.class, () -> MessageCodec.decode("not a json"));
    }

    @Test
    void topicValidation() {
        assertTrue(Topic.isValid("chat.content"));
        assertTrue(Topic.isValid("notify.task.done"));
        assertTrue(Topic.isValid("sys.heartbeat"));
        assertFalse(Topic.isValid(null));
        assertFalse(Topic.isValid("single"));
        assertFalse(Topic.isValid("Chat.Content"));
        assertTrue(Topic.isSystem("sys.ack"));
        assertFalse(Topic.isSystem("chat.content"));
    }
}
