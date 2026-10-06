package com.gj.llm.mcp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.mcp.model.ConnectionTestResult;
import com.gj.llm.mcp.model.McpServerConfigRequest;
import com.gj.llm.mcp.model.McpServerConfigVO;
import com.gj.llm.mcp.model.McpToolVO;

import java.util.List;

/**
 * 外部 MCP Server 配置服务 -- CRUD / 启停（运行时生效）/ 连接测试 / 工具预览。
 *
 * @author gj-llm
 */
public interface McpServerConfigService {

    McpServerConfigVO create(McpServerConfigRequest request);

    McpServerConfigVO update(Long id, McpServerConfigRequest request);

    IPage<McpServerConfigVO> page(int page, int pageSize);

    /** 启停：启用即建连，停用即断连，运行时生效 */
    void updateEnabled(Long id, boolean enabled);

    /** 删除（同时断开连接） */
    void delete(Long id);

    /** 连接测试：临时建连 + MCP 握手 + 工具发现，不影响常驻连接 */
    ConnectionTestResult testConnect(Long id);

    /** 已启用连接的工具清单预览 */
    List<McpToolVO> listTools(Long id);
}
