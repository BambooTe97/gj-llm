package com.gj.llm.chat.agent;

import com.gj.llm.common.util.StringUtils;
import com.gj.llm.chat.config.ChatProperties;
import com.gj.llm.chat.sse.SseEventBuilder;
import com.gj.llm.rag.model.RoutingDecision;
import com.gj.llm.rag.service.Reference;
import com.gj.llm.rag.service.RetrievalResult;
import com.gj.llm.rag.service.RetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库问答智能体 -- 走 RAG 检索 + 知识库增强回答。
 *
 * <p>检索编排不在此处,而是调用 rag 的 {@link RetrievalService} 门面获取上下文与引用,
 * 自身只负责:组装前置事件(检索提示/引用/无结果)、构建 RAG Prompt、调 LLM。
 * 检索是 rag 的事,对话是 chat 的事,边界清晰。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
public class RagQaAgent extends AbstractLlmAgent {

    private final RetrievalService retrievalService;

    public RagQaAgent(ChatModel chatModel, ChatProperties chatProperties, RetrievalService retrievalService) {
        super(chatModel, chatProperties);
        this.retrievalService = retrievalService;
    }

    @Override
    public String id() {
        return "rag-qa";
    }

    @Override
    protected PreparedPrompt prepare(AgentContext ctx) {
        // 检索目标三级取值:显式锁库 > 智能路由决策 > 空(交由检索服务返回空结果)
        List<Long> targetDatasetIds = resolveTargetDatasets(ctx);
        // 拆解子问题:仅智能路由决策携带且 ≥2 条时生效;显式锁库路径恒为空(边界:锁库不拆解)
        List<String> subQueries = resolveSubQueries(ctx);
        RetrievalResult rr = subQueries.size() >= 2
                ? retrievalService.retrievePerSubQueries(ctx.getUserContent(), subQueries, targetDatasetIds, ctx.getUserId())
                : retrievalService.retrieve(ctx.getUserContent(), targetDatasetIds, ctx.getUserId());
        // 引用暂存到上下文,编排器流结束后随消息持久化(前端历史消息可还原角标与参考来源)
        ctx.setReferences(rr.references());

        boolean decomposed = !rr.noConfidentResult() && subQueries.size() >= 2;
        List<ServerSentEvent<String>> preEvents = new ArrayList<>();
        preEvents.add(SseEventBuilder.event("thinking", Map.of("content", searchingHint(ctx, subQueries))));
        if (rr.noConfidentResult()) {
            preEvents.add(SseEventBuilder.event("no_result",
                    Map.of("message", "知识库中未找到与该问题相关的内容")));
        }
        if (!rr.references().isEmpty()) {
            preEvents.add(SseEventBuilder.event("references", Map.of("items", rr.references())));
        }

        String systemPrompt = rr.noConfidentResult()
                ? buildNoResultPrompt()
                : buildSystemPrompt(rr.context(), decomposed);
        String userPrompt = buildUserPrompt(ctx.getUserContent(), rr.context());
        return new PreparedPrompt(preEvents, buildMessageList(systemPrompt, userPrompt, ctx));
    }

    /** 检索目标:显式锁库(request 直传)优先,否则取路由决策的目标库列表 */
    private List<Long> resolveTargetDatasets(AgentContext ctx) {
        if (ctx.getDatasetId() != null) {
            return List.of(ctx.getDatasetId());
        }
        RoutingDecision decision = ctx.getRoutingDecision();
        return (decision != null && decision.datasetIds() != null) ? decision.datasetIds() : List.of();
    }

    /** 拆解子问题:仅智能路由决策携带且 ≥2 条时生效;显式锁库/旧路径返回空列表(锁库不拆解) */
    private List<String> resolveSubQueries(AgentContext ctx) {
        if (ctx.getDatasetId() != null) {
            return List.of();
        }
        RoutingDecision decision = ctx.getRoutingDecision();
        return (decision != null && decision.decomposed()) ? decision.subQueries() : List.of();
    }

    /** 检索提示:拆解时先展示子问题,再带目标库名,让用户看到系统的检索计划(无库时退化为通用提示) */
    private String searchingHint(AgentContext ctx, List<String> subQueries) {
        String datasets;
        RoutingDecision decision = ctx.getRoutingDecision();
        if (decision != null && !decision.datasetNames().isEmpty()) {
            datasets = "正在检索知识库: " + String.join("、", decision.datasetNames()) + "...";
        } else {
            datasets = "正在检索知识库...";
        }
        if (subQueries.size() >= 2) {
            return "已拆分子问题: " + circledList(subQueries) + ";" + datasets;
        }
        return datasets;
    }

    /** 子问题带圈编号列表:①xxx ②yyy(超出带圈表回退普通数字) */
    private String circledList(List<String> subQueries) {
        char[] circled = {'①', '②', '③', '④', '⑤'};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < subQueries.size(); i++) {
            String label = i < circled.length ? String.valueOf(circled[i]) : String.valueOf(i + 1);
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(label).append(subQueries.get(i));
        }
        return sb.toString();
    }

    // ==================== RAG Prompt 构建(本智能体私有) ====================

    private String buildSystemPrompt(String context, boolean decomposed) {
        if (StringUtils.isNotBlank(context)) {
            if (decomposed) {
                // 拆解检索:上下文按子问题组织(片段头带子问题标注),要求逐子问题覆盖、缺口如实声明
                return """
                        你是一个智能知识库助手。参考上下文按子问题组织，每个片段标注了它对应的子问题（子问题①②…）。
                        回答要求：
                        1. 逐条覆盖所有子问题，不要遗漏，回答顺序与子问题顺序一致。
                        2. 某个子问题在参考上下文中没有对应片段时，该部分基于你的通用知识回答，并在该部分末尾注明"（未经知识库验证）"。
                        3. 引用标注要求不变：引用了参考上下文的句子末尾标注片段编号，无依据内容不标注。

                        引用标注要求：
                        1. 回答中凡引用了参考上下文的知识，请在对应句子末尾标注片段编号，格式如 [1]，多个片段可连写如 [1][3]。
                        2. 编号必须使用参考上下文中真实存在的【片段n】编号，严禁编造不存在的编号。
                        3. 上下文中没有依据的内容不要标注编号。
                        """;
            }
            return """
                    你是一个智能知识库助手。请根据【参考上下文】回答用户的问题。
                    如果上下文中没有答案或信息不足，请诚实地告诉用户你不知道，不要编造。
                    回答时请保持专业、准确、简洁。

                    引用标注要求：
                    1. 回答中凡引用了参考上下文的知识，请在对应句子末尾标注片段编号，格式如 [1]，多个片段可连写如 [1][3]。
                    2. 编号必须使用参考上下文中真实存在的【片段n】编号，严禁编造不存在的编号。
                    3. 上下文中没有依据的内容不要标注编号。
                    """;
        }
        return """
                你是一个智能AI助手。请根据你的知识回答用户的问题。
                保持专业、准确、友好的回答风格。
                """;
    }

    /** 无可靠检索结果时的系统提示 -- 引导 LLM 诚实告知而非编造。 */
    private String buildNoResultPrompt() {
        return """
                你是一个智能知识库助手。当前知识库中未检索到与用户问题相关的内容。
                请告知用户：知识库中暂无相关内容，无法回答该问题。
                可以建议用户换一种提问方式，或补充相关文档到知识库。不要编造答案。
                """;
    }

    /** 构建当前用户消息(含 RAG 上下文,历史对话通过独立 message 角色传递) */
    private String buildUserPrompt(String currentQuestion, String context) {
        if (StringUtils.isNotBlank(context)) {
            return "参考上下文:\n" + context + "\n\n用户问题:\n" + currentQuestion;
        }
        return currentQuestion;
    }
}
