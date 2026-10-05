package com.gj.llm.rag.service;

import com.gj.llm.rag.model.TestRankedResult;

import java.util.List;

/**
 * 检索服务门面 -- 对外暴露的检索能力出口。
 *
 * <p>检索的完整编排(查询改写 -> 多路粗排 -> Cross-Encoder 精排 -> 父子召回去重 -> 质量护栏)
 * 收敛在 rag 模块内部,对话层(chat)只通过此门面获取 {@link RetrievalResult},
 * 不直接接触 ES / Reranker 等检索基础设施。</p>
 *
 * @author gj-llm
 */
public interface RetrievalService {

    /**
     * 对指定知识库执行检索编排,返回上下文 + 引用 + 是否无可靠结果。
     *
     * @param query     用户原始查询
     * @param datasetId 知识库 ID;为 null 时返回 {@link RetrievalResult#empty()}(通用对话)
     * @param userId    发起检索的用户 ID(可见域终检);null 或无权时返回 empty(fail-closed)
     * @return 检索结果,永不抛异常(内部失败时返回 empty)
     */
    RetrievalResult retrieve(String query, Long datasetId, Long userId);

    /**
     * 多知识库检索编排(chat 智能路由调用):多库并发粗排 -> 跨库合并去重 ->
     * 统一精排(Cross-Encoder 分数全局可比) -> 父子召回去重 -> 按各自来源库阈值过滤。
     * 引用片段逐条携带各自库的名称/ID,支撑多库回答的出处标注。
     *
     * @param query      用户原始查询
     * @param datasetIds 目标知识库 ID 列表;空/null 时返回 {@link RetrievalResult#empty()}
     * @param userId     发起检索的用户 ID(可见域终检,与可见集求交集);null 或交集为空时返回 empty
     * @return 检索结果,永不抛异常(内部失败时返回 empty)
     */
    RetrievalResult retrieve(String query, List<Long> datasetIds, Long userId);

    /**
     * 子问题拆解检索编排:对每个检索单元(原始问题 + 各子问题)独立跑完整检索管线
     * (改写 -> 多库并发粗排 -> 精排 -> 父子去重 -> 阈值护栏),再跨单元去重、轮转交错合并,
     * 全局重编号构建上下文;引用片段携带 subQueryIndex/subQueryText 标注来源子问题。
     *
     * @param originalQuery 用户原始问题(安全网检索单元,受配置 include-original 控制)
     * @param subQueries    拆解出的子问题
     * @param datasetIds    目标知识库 ID 列表(所有子问题共用)
     * @param userId        发起检索的用户 ID(可见域终检,与可见集求交集);null 或交集为空时返回 empty
     * @return 检索结果,永不抛异常(单个单元失败跳过,全部失败返回 empty)
     */
    RetrievalResult retrievePerSubQueries(String originalQuery, List<String> subQueries, List<Long> datasetIds, Long userId);

    /**
     * 精排测试 -- 单查询走 hybrid 粗排 + reranker 精排,返回双分对照(不过阈值、不去父块),
     * 供检索测试页面评估排序效果。reranker 不可用时降级为粗排(rerankScore=coarseScore)。
     *
     * @param query     查询文本
     * @param datasetId 知识库 ID
     * @param topK      最终返回数量
     * @return 含 reranker 可用状态与排序结果,永不抛异常
     */
    TestRankedResult retrieveRanked(String query, Long datasetId, int topK);
}
