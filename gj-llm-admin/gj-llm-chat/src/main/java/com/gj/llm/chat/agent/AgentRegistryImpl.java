package com.gj.llm.chat.agent;

import com.gj.llm.chat.config.ChatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体注册表实现 -- 启动时收集所有内部 Agent bean + 配置声明的 remote Agent。
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class AgentRegistryImpl implements AgentRegistry {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    public AgentRegistryImpl(List<Agent> internalAgents,
                             ChatProperties chatProperties,
                             WebClient.Builder webClientBuilder) {
        // 内部智能体:Spring bean 自动注入
        for (Agent a : internalAgents) {
            agents.put(a.id(), a);
            log.info("[AgentRegistry] 注册内部智能体: {}", a.id());
        }
        // 外部智能体:配置驱动,逐个构造适配器
        chatProperties.getAgents().forEach((id, cfg) -> {
            if ("remote".equalsIgnoreCase(cfg.getType()) && !agents.containsKey(id)) {
                agents.put(id, new RemoteHttpAgent(id, cfg, webClientBuilder));
                log.info("[AgentRegistry] 注册外部智能体: {} -> {}", id, cfg.getEndpoint());
            }
        });
    }

    @Override
    public Agent get(String id) {
        Agent agent = agents.get(id);
        if (agent == null) {
            throw new IllegalStateException("未注册的智能体: " + id);
        }
        return agent;
    }

    @Override
    public Collection<Agent> all() {
        return agents.values();
    }
}
