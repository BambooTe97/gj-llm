package com.gj.llm.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gj.llm.auth.entity.ResourceAclEntity;
import com.gj.llm.auth.mapper.ResourceAclMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 资源授权写服务 —— resource_acl 表的幂等授权 / 回收 / 查询 / 级联清理。
 *
 * <p>只管 ACL 行本身；可见域组合（visibility/owner/grants）在业务模块
 * （gj-llm-rag 的 DatasetVisibleService）。每次写后失效授权缓存 ——
 * 角色主体授权的影响面不可知，按 {@code auth:grants:*} 模式全清。</p>
 *
 * <p><b>红线</b>：不触碰 ThreadLocal，操作者 userId 由调用方显式传入。</p>
 *
 * @author gj-llm
 */
@Service
@RequiredArgsConstructor
public class ResourceAclService {

    private final ResourceAclMapper resourceAclMapper;
    private final GrantService grantService;

    /**
     * 授权（幂等）：同一 (资源, 主体) 重复授权不报错、不产生重复行。
     *
     * @return 本次的 ACL 行 ID（已存在则返回既有行 ID）
     */
    @Transactional
    public Long grant(String resourceType, Long resourceId, String principalType, Long principalId, Long createdBy) {
        ResourceAclEntity existing = resourceAclMapper.selectOne(new LambdaQueryWrapper<ResourceAclEntity>()
                .eq(ResourceAclEntity::getResourceType, resourceType)
                .eq(ResourceAclEntity::getResourceId, resourceId)
                .eq(ResourceAclEntity::getPrincipalType, principalType)
                .eq(ResourceAclEntity::getPrincipalId, principalId)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }
        ResourceAclEntity entity = ResourceAclEntity.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .principalType(principalType)
                .principalId(principalId)
                .createdBy(createdBy)
                .build();
        resourceAclMapper.insert(entity);
        grantService.invalidateAllGrants();
        return entity.getId();
    }

    /**
     * 回收授权：按 ACL 行 ID 删除，并校验归属资源（防止跨资源误删）。
     *
     * @return 是否实际删除了行
     */
    @Transactional
    public boolean revoke(Long aclId, String resourceType, Long resourceId) {
        int deleted = resourceAclMapper.delete(new LambdaQueryWrapper<ResourceAclEntity>()
                .eq(ResourceAclEntity::getId, aclId)
                .eq(ResourceAclEntity::getResourceType, resourceType)
                .eq(ResourceAclEntity::getResourceId, resourceId));
        if (deleted > 0) {
            grantService.invalidateAllGrants();
        }
        return deleted > 0;
    }

    /**
     * 资源的授权列表（按授权时间倒序）。
     */
    public List<ResourceAclEntity> listByResource(String resourceType, Long resourceId) {
        return resourceAclMapper.selectList(new LambdaQueryWrapper<ResourceAclEntity>()
                .eq(ResourceAclEntity::getResourceType, resourceType)
                .eq(ResourceAclEntity::getResourceId, resourceId)
                .orderByDesc(ResourceAclEntity::getCreatedAt));
    }

    /**
     * 资源级联清理（删库时调用）。
     *
     * @return 删除的授权行数
     */
    @Transactional
    public long deleteByResource(String resourceType, Long resourceId) {
        long deleted = resourceAclMapper.delete(new LambdaQueryWrapper<ResourceAclEntity>()
                .eq(ResourceAclEntity::getResourceType, resourceType)
                .eq(ResourceAclEntity::getResourceId, resourceId));
        if (deleted > 0) {
            grantService.invalidateAllGrants();
        }
        return deleted;
    }
}
