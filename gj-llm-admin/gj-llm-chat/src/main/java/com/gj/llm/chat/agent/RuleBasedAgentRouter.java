package com.gj.llm.chat.agent;

import com.gj.llm.chat.config.ChatProperties;
import com.gj.llm.rag.model.RoutingDecision;
import com.gj.llm.rag.service.QueryPlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 智能体路由器 -- 三级决策:显式锁库 &gt; 智能规划 &gt; 内部降级。
 *
 * <p>① 显式锁库:request 直传 datasetId 时不做规划,直接走检索智能体(兼容既有 API 集成);
 * ② 智能规划:{@link QueryPlanner} 判定意图与目标库,决策写入 ctx 供检索智能体取库,
 * chat 意图走 defaultAgent闲聊,retrieve 意图走检索智能体;
 * ③ 降级:planner 永不抛异常,超时/失败在其内部降级为扇出检索,路由器无需兜底。</p>
 *
 * <p>路由只消费 {@link RoutingDecision},规划细节(QueryRewriter/检索)全在 rag 模块内。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleBasedAgentRouter implements AgentRouter {

    private final AgentRegistry registry;
    private final ChatProperties chatProperties;
    private final QueryPlanner queryPlanner;

    @Override
    public Agent route(AgentContext ctx) {
        ChatProperties.Routing routing = chatProperties.getRouting();

        // ① 显式锁库:datasetId 直传,保留既有直连语义(历史集成与测试入口)
        if (ctx.getDatasetId() != null) {
            return registry.get(ragAgent(routing));
        }

        // ② 智能规划:判定意图与目标库,决策暂存到 ctx
        RoutingDecision decision = queryPlanner.plan(ctx.getUserContent());
        ctx.setRoutingDecision(decision);
        log.info("[Router] 智能路由: intent={}, datasetIds={}, datasetNames={}",
                decision.intent(), decision.datasetIds(), decision.datasetNames());
        return registry.get(decision.retrieve() ? ragAgent(routing) : routing.getDefaultAgent());
    }

    /** 检索型智能体名:取 hasDataset 规则配置的 agent(该规则语义即"有库走检索") */
    private String ragAgent(ChatProperties.Routing routing) {
        return routing.getRules().stream()
                .filter(ChatProperties.Rule::isHasDataset)
                .map(ChatProperties.Rule::getAgent)
                .findFirst()
                .orElse(routing.getDefaultAgent());
    }
}
