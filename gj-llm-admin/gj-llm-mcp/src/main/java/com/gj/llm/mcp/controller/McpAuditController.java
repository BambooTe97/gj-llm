package com.gj.llm.mcp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.common.web.R;
import com.gj.llm.mcp.entity.McpAuditLogEntity;
import com.gj.llm.mcp.service.McpAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP 审计日志查询（RBAC：mcp:audit:view）。
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/v1/mcp/audits")
@RequiredArgsConstructor
public class McpAuditController {

    private final McpAuditService mcpAuditService;

    @GetMapping
    public R<IPage<McpAuditLogEntity>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) String toolName,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String direction) {
        return R.ok(mcpAuditService.page(page, pageSize, userId, toolName, status, direction));
    }
}
