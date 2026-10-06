package com.gj.llm.mcp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.common.web.R;
import com.gj.llm.mcp.model.McpApiKeyCreateRequest;
import com.gj.llm.mcp.model.McpApiKeyVO;
import com.gj.llm.mcp.model.McpStatusUpdateRequest;
import com.gj.llm.mcp.service.McpApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP API Key 管理（RBAC：mcp:key:*，接口自动扫描 + ApiAutoLinker 关联）。
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/v1/mcp/api-keys")
@RequiredArgsConstructor
public class McpApiKeyController {

    private final McpApiKeyService mcpApiKeyService;

    /** 发放新 Key（fullKey 仅本次响应返回） */
    @PostMapping
    public R<McpApiKeyVO> create(@RequestBody McpApiKeyCreateRequest request) {
        return R.ok(mcpApiKeyService.create(request));
    }

    @GetMapping
    public R<IPage<McpApiKeyVO>> page(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(mcpApiKeyService.page(page, pageSize));
    }

    /** 启用/停用 */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody McpStatusUpdateRequest request) {
        mcpApiKeyService.updateStatus(id, request.getStatus());
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mcpApiKeyService.delete(id);
        return R.ok(null);
    }
}
