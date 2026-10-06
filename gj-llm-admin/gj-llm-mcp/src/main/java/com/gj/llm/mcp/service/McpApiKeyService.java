package com.gj.llm.mcp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.mcp.entity.McpApiKeyEntity;
import com.gj.llm.mcp.model.McpApiKeyCreateRequest;
import com.gj.llm.mcp.model.McpApiKeyVO;

/**
 * MCP API Key 服务 -- 发放 / 吊销 / 校验（server 对外认证链的核心）。
 *
 * @author gj-llm
 */
public interface McpApiKeyService {

    /** 发放新 Key：明文仅本次响应返回（VO.fullKey），库内只存哈希 */
    McpApiKeyVO create(McpApiKeyCreateRequest request);

    /** 分页列表（不含哈希） */
    IPage<McpApiKeyVO> page(int page, int pageSize);

    /** 启用/停用 */
    void updateStatus(Long id, Integer status);

    /** 删除（吊销） */
    void delete(Long id);

    /**
     * 认证链校验：按明文 key 定位（SHA-256 → key_hash 唯一索引），校验启停与有效期，
     * 通过则异步触摸 last_used_at 并返回实体；任何失败返回 null（调用方统一 401，防探测）。
     */
    McpApiKeyEntity validateByKey(String rawKey);
}
