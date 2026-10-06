package com.gj.llm.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gj.llm.mcp.entity.McpAuditLogEntity;
import com.gj.llm.mcp.mapper.McpAuditLogMapper;
import com.gj.llm.mcp.service.McpAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;




/**
 * MCP 审计查询服务实现。
 *
 * @author gj-llm
 */
@Service
@RequiredArgsConstructor
public class McpAuditServiceImpl implements McpAuditService {

    private final McpAuditLogMapper auditLogMapper;

    @Override
    public IPage<McpAuditLogEntity> page(int page, int pageSize, Long userId,
                                         String toolName, String status, String direction) {
        String tool = normalize(toolName);
        String st = normalize(status);
        String dir = normalize(direction);
        LambdaQueryWrapper<McpAuditLogEntity> wrapper = new LambdaQueryWrapper<McpAuditLogEntity>()
                .eq(userId != null, McpAuditLogEntity::getUserId, userId)
                .like(tool != null, McpAuditLogEntity::getToolName, tool)
                .eq(st != null, McpAuditLogEntity::getResultStatus, st)
                .eq(dir != null, McpAuditLogEntity::getDirection, dir)
                .orderByDesc(McpAuditLogEntity::getCreatedAt);
        return auditLogMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    /** 空串归一为 null（条件不生效） */
    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
