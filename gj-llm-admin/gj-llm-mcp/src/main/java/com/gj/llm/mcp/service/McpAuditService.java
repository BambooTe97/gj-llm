package com.gj.llm.mcp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.mcp.entity.McpAuditLogEntity;

/**
 * MCP 审计查询服务（落库走异步监听器，此处只读）。
 *
 * @author gj-llm
 */
public interface McpAuditService {

    /** 分页查询：条件均可空（userId 精确 / toolName 模糊 / status、direction 精确） */
    IPage<McpAuditLogEntity> page(int page, int pageSize, Long userId,
                                  String toolName, String status, String direction);
}
