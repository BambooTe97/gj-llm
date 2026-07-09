package com.gj.llm.agent.controller;

import com.gj.llm.agent.vector.DynamicVectorStoreManager;
import com.gj.llm.common.web.ApiResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ai")
public class AgentChatController {
    private final DynamicVectorStoreManager storeManager;
    private final ChatClient.Builder chatClientBuilder;

    public AgentChatController(DynamicVectorStoreManager storeManager, ChatClient.Builder chatClientBuilder) {
        this.storeManager = storeManager;
        this.chatClientBuilder = chatClientBuilder;
    }

    @PostMapping("/api/agent/ask")
    public ApiResponse<Map<String, Object>> askAgent(@RequestBody AgentRequest request) {

        // 1. 动态获取向量库
        VectorStore vectorStore = storeManager.getVectorStore(request.getType());

        // 2. 检索 Top 5 相似片段
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.getQuestion())
                        .topK(5)
                        .similarityThreshold(0.75)
                        .build()
        );

        // 3. 组装上下文
        String context = similarDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        // 4. 根据类型动态切换人设
        String systemPrompt = switch (request.getType()) {
            case "medical" ->
                    "你是一个专业的医疗助手。请根据【参考上下文】回答用户的问题。如果上下文中没有答案，请诚实地告诉用户你不知道，不要编造。";
            case "story" -> "你是一个充满想象力的讲故事大师。请根据【参考上下文】创作或回答。";
            default -> "你是一个智能助手。请根据【参考上下文】回答问题。";
        };

        // 5. 构建 Prompt
        String userContent = String.format("""
                参考上下文:
                %s

                用户问题:
                %s
                """, context, request.getQuestion());

        // 6. 调用 ChatClient
        ChatClient chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .build();

        String answer = chatClient.prompt()
                .user(userContent)
                .call()
                .content();

        Map<String, Object> data = new HashMap<>();
        data.put("answer", answer);
        data.put("type", request.getType());
        return ApiResponse.ok(data);
    }
}
