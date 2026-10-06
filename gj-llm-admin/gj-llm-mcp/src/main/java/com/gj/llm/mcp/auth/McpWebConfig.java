package com.gj.llm.mcp.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MCP Web 配置 -- 注册 API Key 拦截器（仅拦 /open/mcp/**，不碰其余路径）。
 *
 * <p>与 gj-base-admin 的 WebMvcConfig 并存（Spring 收集所有 WebMvcConfigurer），
 * MCP 端点挂 /open/mcp** 以复用 /open/** 的双层放行（Security permitAll +
 * ApiPermissionInterceptor 排除），认证完全由本模块的 API Key 闸门承担。</p>
 *
 * @author gj-llm
 */
@Configuration
@RequiredArgsConstructor
public class McpWebConfig implements WebMvcConfigurer {

    private final McpApiKeyAuthInterceptor mcpApiKeyAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mcpApiKeyAuthInterceptor)
                .addPathPatterns("/open/mcp/**");
    }
}
