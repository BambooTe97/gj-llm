package com.gj.llm.chat.service;

import com.gj.llm.chat.model.ChatRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 对话服务接口 —— RAG 增强的流式对话。
 *
 * @author gj-llm
 */
public interface ChatService {

    /** 流式对话（Reactive SSE），携带 RAG 检索增强 */
    Flux<ServerSentEvent<String>> chatStream(ChatRequest request);
}
