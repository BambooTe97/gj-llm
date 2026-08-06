package com.gj.llm.chat.agent;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 智能体统一契约 -- chat 编排平台的核心抽象。
 *
 * <p>任何智能体(内部 Java 实现、外部 HTTP API、MCP 工具)只要实现此契约,
 * 就能被 {@link AgentRegistry} 注册、被 {@link AgentRouter} 路由、被编排器调用。
 * 加智能体 = 加一个实现类或一段配置,不改编排主干(开闭原则)。</p>
 *
 * <p>{@code stream} 只产出智能体自身的事件(thinking/references/content/no_result),
 * 不负责 done 事件与持久化 -- 那是编排器(对话层)的职责。
 * 流式过程中智能体把累积答案写入 {@link AgentContext} 的缓冲,供编排器收尾。</p>
 *
 * @author gj-llm
 */
public interface Agent {

    /** 智能体唯一标识,用于注册表查找与路由配置,如 "rag-qa" / "chitchat" / "dify-cs"。 */
    String id();

    /**
     * 流式执行,产出该智能体的 SSE 事件流。
     *
     * @param ctx 对话上下文(含用户消息、历史、数据集、累积缓冲)
     * @return SSE 事件流(不含 done 事件)
     */
    Flux<ServerSentEvent<String>> stream(AgentContext ctx);
}
