package com.gj.llm.mcp.auth;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.common.web.R;
import com.gj.llm.mcp.auth.McpUserContext.McpPrincipal;
import com.gj.llm.mcp.entity.McpApiKeyEntity;
import com.gj.llm.mcp.service.McpApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * MCP API Key 认证拦截器 -- 拦截 /open/mcp/**（/open/** 已被 Security 与权限拦截器放行，
 * 本拦截器是 MCP 端点的唯一认证闸门）。
 *
 * <p>凭据取 X-Api-Key 或 Authorization: Bearer；校验通过后写入 {@link McpUserContext}
 * 供工具执行时做可见域隔离与审计，afterCompletion 清理防线程池串号。
 * 失败一律 401 且不区分原因，防探测。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpApiKeyAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_API_KEY = "X-Api-Key";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final McpApiKeyService apiKeyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String rawKey = extractKey(request);
        McpApiKeyEntity entity = rawKey == null ? null : apiKeyService.validateByKey(rawKey);
        if (entity == null) {
            writeUnauthorized(response);
            return false;
        }
        McpUserContext.set(new McpPrincipal(entity.getUserId(), entity.getUsername(),
                entity.getId(), clientIp(request)));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        McpUserContext.clear();
    }

    /** 取明文 key：X-Api-Key 优先，其次 Authorization: Bearer */
    private String extractKey(HttpServletRequest request) {
        String key = request.getHeader(HEADER_API_KEY);
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        String auth = request.getHeader(HEADER_AUTHORIZATION);
        if (auth != null && auth.startsWith(BEARER_PREFIX)) {
            return auth.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JacksonUtils.toJson(R.unauthorized("API Key 无效或已过期")));
    }
}
