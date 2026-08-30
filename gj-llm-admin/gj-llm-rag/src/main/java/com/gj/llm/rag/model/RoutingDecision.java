package com.gj.llm.rag.model;

import java.util.List;

/**
 * 智能路由决策 -- {@link com.gj.llm.rag.service.QueryPlanner} 的输出。
 *
 * <p>由 chat 层路由器消费(按意图分发智能体),由 RagQaAgent 消费(多库检索目标);
 * 两个消费方只读,不做再加工。</p>
 *
 * <p>注意:每条检索目标自带库信息,不依赖会话级单一知识库假设,
 * 支撑"自动路由/多知识库并发检索"形态。</p>
 *
 * @param intent       意图:CHAT(闲聊,无需检索) / RETRIEVE(需要知识库检索)
 * @param datasetIds   检索目标知识库 ID(intent=RETRIEVE 时非空;多库并发检索)
 * @param datasetNames 对应知识库名称(供"正在检索知识库: xxx"提示展示,与 ids 同序)
 * @author gj-llm
 */
public record RoutingDecision(Intent intent, List<Long> datasetIds, List<String> datasetNames) {

    /** 用户意图 */
    public enum Intent {
        /** 闲聊/问候/与知识库无关,直接 LLM 对话 */
        CHAT,
        /** 需要知识库检索后回答 */
        RETRIEVE
    }

    public boolean retrieve() {
        return intent == Intent.RETRIEVE;
    }

    /** 闲聊决策(无知识库可检或意图判定为闲聊时使用) */
    public static RoutingDecision chat() {
        return new RoutingDecision(Intent.CHAT, List.of(), List.of());
    }
}
