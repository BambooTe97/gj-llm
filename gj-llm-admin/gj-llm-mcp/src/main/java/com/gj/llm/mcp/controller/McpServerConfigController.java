package com.gj.llm.mcp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.common.web.R;
import com.gj.llm.mcp.model.ConnectionTestResult;
import com.gj.llm.mcp.model.McpEnabledUpdateRequest;
import com.gj.llm.mcp.model.McpServerConfigRequest;
import com.gj.llm.mcp.model.McpServerConfigVO;
import com.gj.llm.mcp.model.McpToolVO;
import com.gj.llm.mcp.service.McpServerConfigService;
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

import java.util.List;

/**
 * 外部 MCP Server 管理（RBAC：mcp:server:*）-- CRUD/启停运行时生效/连接测试/工具预览。
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/v1/mcp/servers")
@RequiredArgsConstructor
public class McpServerConfigController {

    private final McpServerConfigService mcpServerConfigService;

    @PostMapping
    public R<McpServerConfigVO> create(@RequestBody McpServerConfigRequest request) {
        return R.ok(mcpServerConfigService.create(request));
    }

    @PutMapping("/{id}")
    public R<McpServerConfigVO> update(@PathVariable Long id, @RequestBody McpServerConfigRequest request) {
        return R.ok(mcpServerConfigService.update(id, request));
    }

    @GetMapping
    public R<IPage<McpServerConfigVO>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(mcpServerConfigService.page(page, pageSize));
    }

    /** 启停：启用即建连、停用即断连，运行时生效 */
    @PutMapping("/{id}/enabled")
    public R<Void> updateEnabled(@PathVariable Long id, @RequestBody McpEnabledUpdateRequest request) {
        mcpServerConfigService.updateEnabled(id, Boolean.TRUE.equals(request.getEnabled()));
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mcpServerConfigService.delete(id);
        return R.ok(null);
    }

    /** 连接测试：临时建连 + MCP 握手 + 工具发现 */
    @PostMapping("/{id}/test")
    public R<ConnectionTestResult> testConnect(@PathVariable Long id) {
        return R.ok(mcpServerConfigService.testConnect(id));
    }

    /** 工具清单预览 */
    @GetMapping("/{id}/tools")
    public R<List<McpToolVO>> listTools(@PathVariable Long id) {
        return R.ok(mcpServerConfigService.listTools(id));
    }
}
