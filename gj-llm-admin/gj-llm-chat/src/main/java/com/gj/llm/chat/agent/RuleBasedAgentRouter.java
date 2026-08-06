package com.gj.llm.chat.agent;

import com.gj.llm.chat.config.ChatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 规则路由器 -- 按 {@link ChatProperties.Routing} 的规则顺序匹配,首个命中生效,否则走默认。
 *
 * <p>本期条件:hasDataset(datasetId != null)。结构支持后续扩展更多条件或换 LLM 路由。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleBasedAgentRouter implements AgentRouter {

    private final AgentRegistry registry;
    private final ChatProperties chatProperties;

    @Override
    public Agent route(AgentContext ctx) {
        ChatProperties.Routing routing = chatProperties.getRouting();
        for (ChatProperties.Rule rule : routing.getRules()) {
            if (matches(rule, ctx)) {
                return registry.get(rule.getAgent());
            }
        }
        return registry.get(routing.getDefaultAgent());
    }

    private boolean matches(ChatProperties.Rule rule, AgentContext ctx) {
        if (rule.isHasDataset() && ctx.getDatasetId() != null) {
            return true;
        }
        return false;
    }
}
