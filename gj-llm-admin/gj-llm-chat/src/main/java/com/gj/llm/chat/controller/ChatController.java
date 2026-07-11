package com.gj.llm.chat.controller;

import com.gj.llm.chat.model.ChatRequest;
import com.gj.llm.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 对话控制器 —— 流式 SSE 对话（Reactive Flux + RAG 增强）。
 *
 * @author gj-llm
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 流式发送消息（SSE）。
     *
     * <p>返回 {@code text/event-stream}，前端通过 fetch + ReadableStream 读取。
     * SSE 事件类型：thinking / references / content / done / error
     */
    @PostMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendStream(@RequestBody ChatRequest request) {
        log.info("收到流式对话请求: conversationId={}, content={}", request.getConversationId(), request.getContent());
        return chatService.chatStream(request);
    }
}
