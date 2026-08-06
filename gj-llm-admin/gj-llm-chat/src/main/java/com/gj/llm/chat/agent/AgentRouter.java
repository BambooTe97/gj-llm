package com.gj.llm.chat.agent;

/**
 * 智能体路由器 -- 决定一个请求由哪个 {@link Agent} 处理。
 *
 * <p>本身也是策略:本期规则路由,后续可换 LLM 意图路由或多步 Planner,不影响编排器。</p>
 *
 * @author gj-llm
 */
public interface AgentRouter {

    /** 为当前上下文选择智能体 */
    Agent route(AgentContext ctx);
}
