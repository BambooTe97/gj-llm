package com.gj.llm.chat.agent;

import com.gj.llm.chat.config.ChatProperties;
import com.gj.llm.chat.sse.SseEventBuilder;
import com.gj.llm.mcp.client.McpToolRegistry;
import com.gj.llm.mcp.client.ToolCallListener;
import com.gj.llm.mcp.client.ToolCallSpyCallback;
import com.gj.llm.mcp.config.McpProperties;
import com.gj.llm.mcp.server.tool.KnowledgeSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具循环智能体 -- 模型自主决定何时检索/调外部工具（区别于 rag-qa 的固定前置检索）。
 *
 * <p>工具集合：内置知识库检索（复用 mcp 模块的 {@link KnowledgeSearchTool}，
 * 可见域隔离与检索管线同一套护栏）+ 已启用外部 MCP server 的聚合工具
 * （{@link McpToolRegistry}，失败隔离在注册表内完成）。</p>
 *
 * <p>逐请求包装 {@link ToolCallSpyCallback}：发 tool_call/tool_result SSE 事件 +
 * CLIENT_CALL 审计；工具抛错降级为错误说明返回给模型，对话不中断。
 * 用户身份经 toolContext 显式下传（工具可能在 reactor 线程执行，SecurityContext 不可靠）。</p>
 *
 * <p>注意：工具循环要求对话模型支持 function calling（gemma2 不支持，qwen3 等支持）；
 * 本智能体独立注册，出问题路由切回 rag-qa 链路，互不影响。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
public class ToolCallAgent extends AbstractLlmAgent {

    private final McpToolRegistry mcpToolRegistry;
    private final McpProperties mcpProperties;
    private final ApplicationEventPublisher publisher;

    /** 内置知识库检索工具（构造时一次性转 ToolCallback，@Tool 方法反射元数据不变） */
    private final ToolCallback[] internalToolCallbacks;

    public ToolCallAgent(ChatModel chatModel, ChatProperties chatProperties,
                         KnowledgeSearchTool knowledgeSearchTool,
                         McpToolRegistry mcpToolRegistry,
                         McpProperties mcpProperties,
                         ApplicationEventPublisher publisher) {
        super(chatModel, chatProperties);
        this.mcpToolRegistry = mcpToolRegistry;
        this.mcpProperties = mcpProperties;
        this.publisher = publisher;
        this.internalToolCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(knowledgeSearchTool)
                .build()
                .getToolCallbacks();
    }

    @Override
    public String id() {
        return "tool-call";
    }

    @Override
    protected PreparedPrompt prepare(AgentContext ctx) {
        // 无前置事件（检索由模型按需发起），引用溯源经 tool_result 事件透出
        return new PreparedPrompt(List.of(),
                buildMessageList(systemPrompt(), ctx.getUserContent(), ctx));
    }

    @Override
    protected ChatClient.ChatClientRequestSpec customizeRequest(ChatClient.ChatClientRequestSpec spec,
                                                                AgentContext ctx) {
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        for (ToolCallback cb : internalToolCallbacks) {
            toolCallbacks.add(spy(cb, null, ctx));
        }
        try {
            for (McpToolRegistry.ServerToolCallbacks server : mcpToolRegistry.externalCallbacks()) {
                for (ToolCallback cb : server.callbacks()) {
                    toolCallbacks.add(spy(cb, server.serverName(), ctx));
                }
            }
        } catch (Exception e) {
            // 失败隔离：外部工具聚合异常只降级为内置工具，不阻塞对话
            log.warn("[Agent:{}] 外部工具聚合失败,降级为仅内置工具: {}", id(), e.getMessage());
        }
        if (toolCallbacks.isEmpty()) {
            return spec;
        }
        // 身份经 toolContext 下传（KnowledgeSearchTool 在 reactor 线程执行时解析）
        Long userId = ctx.getUserId() == null ? -1L : ctx.getUserId();
        return spec.toolCallbacks(toolCallbacks)
                .toolContext(Map.of("userId", userId));
    }

    @Override
    protected Flux<ServerSentEvent<String>> sideChannel(AgentContext ctx) {
        return ctx.toolEventSink().asFlux();
    }

    /** 包 Spy：SSE 旁路事件 + CLIENT_CALL 审计 + 降级兜底 */
    private ToolCallback spy(ToolCallback delegate, String serverName, AgentContext ctx) {
        return new ToolCallSpyCallback(delegate, serverName, ctx.getUserId(), null,
                sseListener(ctx), publisher, mcpProperties.getAudit().getParamsMaxLength());
    }

    /** 工具事件 → tool_call/tool_result SSE（与 thinking/references 同风格） */
    private ToolCallListener sseListener(AgentContext ctx) {
        return new ToolCallListener() {
            @Override
            public void onCall(String toolName, String argsPreview) {
                ctx.toolEventSink().tryEmitNext(SseEventBuilder.event("tool_call",
                        Map.of("name", toolName, "args", argsPreview == null ? "" : argsPreview)));
            }

            @Override
            public void onResult(String toolName, boolean ok, String resultPreview,
                                 long costMs, String errorMessage) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("name", toolName);
                payload.put("ok", ok);
                payload.put("result", resultPreview == null ? "" : resultPreview);
                payload.put("costMs", costMs);
                if (errorMessage != null) {
                    payload.put("error", errorMessage);
                }
                ctx.toolEventSink().tryEmitNext(SseEventBuilder.event("tool_result", payload));
            }
        };
    }

    private String systemPrompt() {
        return """
                你是一个智能AI助手，可以调用工具完成用户任务。工具使用要求：
                1. 涉及企业知识、文档内容、内部资料的问题，先调用 search_knowledge 工具检索知识库，再基于检索结果回答。
                2. 通用寒暄、常识性问题可直接回答，不要滥用工具。
                3. 工具调用失败时，基于你自己的知识回答，并告知用户工具暂时不可用。
                4. 引用知识库内容回答时，请在对应句子末尾标注来源文件名，格式如（来源: xxx.pdf）。

                回答保持专业、准确、简洁。
                """;
    }
}
