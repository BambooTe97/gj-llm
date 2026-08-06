package com.gj.llm.chat.agent;

import java.util.Collection;

/**
 * 智能体注册表 -- 按 id 查找已注册的 {@link Agent}。
 *
 * <p>来源双轨:内部智能体(Spring bean,如 RagQaAgent/ChitchatAgent)+ 外部智能体
 * (配置驱动,见 {@link com.gj.llm.chat.config.ChatProperties})。统一注册,对路由透明。</p>
 *
 * @author gj-llm
 */
public interface AgentRegistry {

    /** 按 id 获取智能体,不存在时抛异常 */
    Agent get(String id);

    /** 全部已注册智能体 */
    Collection<Agent> all();
}
