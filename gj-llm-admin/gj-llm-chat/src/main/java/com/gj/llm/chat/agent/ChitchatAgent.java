package com.gj.llm.chat.agent;

import com.gj.llm.chat.config.ChatProperties;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 闲聊智能体 -- 无检索,直接用通用知识对话。
 *
 * <p>用于无知识库场景(datasetId 为 null),省去检索开销,走纯 LLM 对话。
 * 与 {@link RagQaAgent} 通过策略隔离,差异化处理而非 if-else。</p>
 *
 * @author gj-llm
 */
@Component
public class ChitchatAgent extends AbstractLlmAgent {

    private static final String SYSTEM_PROMPT = """
            你是一个智能AI助手。请根据你的知识回答用户的问题。
            保持专业、准确、友好的回答风格。
            """;

    public ChitchatAgent(ChatModel chatModel, ChatProperties chatProperties) {
        super(chatModel, chatProperties);
    }

    @Override
    public String id() {
        return "chitchat";
    }

    @Override
    protected PreparedPrompt prepare(AgentContext ctx) {
        // 闲聊无前置事件,无 RAG 上下文
        return new PreparedPrompt(List.of(), buildMessageList(SYSTEM_PROMPT, ctx.getUserContent(), ctx));
    }
}
