package com.gj.llm.mcp.server.tool;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.mcp.auth.McpUserContext;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.service.DatasetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库清单工具 -- 列出调用方可见的知识库（与网页端同一套可见域规则）。
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasetListTool {

    /** 清单预览最大条数（pageForUser 单页） */
    private static final int MAX_DATASETS = 100;

    private final DatasetService datasetService;

    @Tool(name = "list_datasets",
            description = "列出调用方可见的全部企业知识库（含 ID 与简介）。"
                    + "需要限定某个知识库检索时，先调用本工具获取知识库 ID。")
    public String listDatasets(ToolContext toolContext) {
        Long userId = McpUserContext.resolveUserId(toolContext);
        if (userId == null) {
            return JacksonUtils.toJson(Map.of("error", "无法识别调用方身份"));
        }
        IPage<DatasetEntity> page = datasetService.pageForUser(userId, 1, MAX_DATASETS);
        List<Map<String, Object>> datasets = new ArrayList<>();
        for (DatasetEntity ds : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ds.getId());
            item.put("name", ds.getName());
            item.put("description", ds.getDescription());
            item.put("docCount", ds.getDocCount());
            datasets.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotal());
        result.put("datasets", datasets);
        return JacksonUtils.toJson(result);
    }
}
