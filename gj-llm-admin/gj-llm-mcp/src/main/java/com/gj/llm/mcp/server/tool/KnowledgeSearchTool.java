package com.gj.llm.mcp.server.tool;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.mcp.auth.McpUserContext;
import com.gj.llm.mcp.constant.McpConstants;
import com.gj.llm.rag.service.DatasetVisibleService;
import com.gj.llm.rag.service.Reference;
import com.gj.llm.rag.service.RetrievalResult;
import com.gj.llm.rag.service.RetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库检索工具 -- MCP Server 对外暴露的核心能力，chat 工具循环的内置工具复用同一实现。
 *
 * <p>安全边界：以解析到的调用方 userId 求可见域（fail-closed），再交由
 * {@link RetrievalService} 检索门面二次求交，双重防线不越权。
 * 结果 JSON 直出给模型（引用正文超长截断）。</p>
 *
 * <p>注意：{@code retrievalService} 必须保持 {@code @Lazy}——本工具经
 * ToolCallbackProvider 被 toolCallbackResolver 在 ChatModel 构造期间急切收集，
 * 而 RetrievalServiceImpl → QueryRewriter → ChatModel 会成环；惰性代理把真实
 * 检索链的初始化推迟到首次工具调用（届时 ChatModel 已就绪）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
public class KnowledgeSearchTool {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 10;

    private final RetrievalService retrievalService;
    private final DatasetVisibleService datasetVisibleService;

    public KnowledgeSearchTool(@Lazy RetrievalService retrievalService,
                               DatasetVisibleService datasetVisibleService) {
        this.retrievalService = retrievalService;
        this.datasetVisibleService = datasetVisibleService;
    }

    @Tool(name = "search_knowledge",
            description = "在企业知识库中语义检索与问题相关的知识片段。回答知识库类问题前应先调用本工具；"
                    + "默认检索调用方可见的全部知识库，也可用 dataset_id 限定单个知识库。")
    public String searchKnowledge(
            @ToolParam(description = "检索问题或关键词，用与文档一致的表述效果更佳") String query,
            @ToolParam(description = "返回条数，1-10，默认 5", required = false) Integer topK,
            @ToolParam(description = "限定检索的知识库 ID（可先调 list_datasets 获取），不传则检索全部可见知识库",
                    required = false) Long datasetId,
            ToolContext toolContext) {

        Long userId = McpUserContext.resolveUserId(toolContext);
        if (userId == null) {
            return errorResult("无法识别调用方身份，拒绝检索");
        }
        if (query == null || query.isBlank()) {
            return errorResult("检索问题不能为空");
        }
        int limit = topK == null ? DEFAULT_TOP_K : Math.min(Math.max(topK, 1), MAX_TOP_K);

        // 检索范围：显式锁库（须可访问） > 全部可见知识库；空可见集直接 fail-closed
        List<Long> targetDatasetIds;
        if (datasetId != null) {
            if (!datasetVisibleService.canAccessDataset(userId, datasetId)) {
                return errorResult("无权访问知识库 " + datasetId);
            }
            targetDatasetIds = List.of(datasetId);
        } else {
            Set<Long> visible = datasetVisibleService.visibleDatasetIds(userId);
            if (visible.isEmpty()) {
                return notFoundResult("调用方没有可访问的知识库");
            }
            targetDatasetIds = new ArrayList<>(visible);
        }

        log.info("[MCP工具] search_knowledge: user={}, datasets={}, query={}",
                userId, targetDatasetIds.size(), abbreviate(query));
        RetrievalResult rr = retrievalService.retrieve(query, targetDatasetIds, userId);
        if (rr.noConfidentResult() || rr.references().isEmpty()) {
            return notFoundResult("知识库中未检索到与该问题相关的内容");
        }

        List<Map<String, Object>> references = new ArrayList<>();
        for (Reference ref : rr.references()) {
            if (references.size() >= limit) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", ref.rank());
            item.put("content", abbreviate(ref.content()));
            item.put("source", ref.source());
            item.put("datasetName", ref.datasetName());
            item.put("score", ref.score());
            references.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("message", "检索到 " + references.size() + " 条相关片段，content 末尾的 source/datasetName 可作为引用来源");
        result.put("references", references);
        return JacksonUtils.toJson(result);
    }

    private String errorResult(String message) {
        return JacksonUtils.toJson(Map.of("found", false, "error", message));
    }

    private String notFoundResult(String message) {
        return JacksonUtils.toJson(Map.of("found", false, "message", message));
    }

    /** 引用正文截断（返回给模型的内容上限） */
    private String abbreviate(String text) {
        if (text == null || text.length() <= McpConstants.TOOL_CONTENT_MAX_CHARS) {
            return text == null ? "" : text;
        }
        return text.substring(0, McpConstants.TOOL_CONTENT_MAX_CHARS) + "...";
    }
}
