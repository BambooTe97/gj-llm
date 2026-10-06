package com.gj.llm.mcp.server;

import com.gj.llm.mcp.config.McpProperties;
import com.gj.llm.mcp.server.tool.DatasetListTool;
import com.gj.llm.mcp.server.tool.KnowledgeSearchTool;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Arrays;

/**
 * MCP Server 工具暴露装配 -- 把知识库工具以 ToolCallbackProvider Bean 注册，
 * spring-ai-starter-mcp-server-webmvc 自动收集该 Bean 暴露为 MCP 工具。
 *
 * <p>仅收集本 Provider 一个 Bean：chat 侧工具是逐请求构造的，不以 Bean 形式存在，
 * 不会被 server 误暴露。平台开关（gj.llm.mcp.server.enabled=false）时不注册，
 * server 端零工具暴露。</p>
 *
 * @author gj-llm
 */
@Configuration
@RequiredArgsConstructor
public class McpServerToolProvider {

    private final KnowledgeSearchTool knowledgeSearchTool;
    private final DatasetListTool datasetListTool;
    private final McpProperties properties;
    private final ApplicationEventPublisher publisher;

    /** 方法名即 Bean 名，不能与配置类自身的 Bean 名（mcpServerToolProvider）重名；starter 按类型收集，名字无关紧要 */
    @Bean
    @ConditionalOnProperty(prefix = "gj.llm.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ToolCallbackProvider mcpServerToolCallbackProvider() {
        ToolCallback[] raw = MethodToolCallbackProvider.builder()
                .toolObjects(knowledgeSearchTool, datasetListTool)
                .build()
                .getToolCallbacks();
        ToolCallback[] audited = Arrays.stream(raw)
                .map(cb -> (ToolCallback) new AuditedToolCallback(cb, publisher,
                        properties.getAudit().getParamsMaxLength()))
                .toArray(ToolCallback[]::new);
        return () -> audited;
    }
}
