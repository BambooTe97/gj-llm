package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.ResourceAclEntity;
import com.gj.llm.base.mapper.ResourceAclMapper;
import com.gj.llm.base.service.GrantService;
import com.gj.llm.base.service.ResourceAclService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 资源授权服务实现 -- 通过 {@link ResourceAclMapper} 管理 {@code resource_acl}。
 *
 * <p><b>红线</b>：不触碰 ThreadLocal，操作者 userId 由调用方显式传入。</p>
 *
 * @author gj-llm
 */
@Service
@RequiredArgsConstructor
public class ResourceAclServiceImpl extends ServiceImpl<ResourceAclMapper, ResourceAclEntity>
        implements ResourceAclService {

    private final GrantService grantService;

    @Override
    @Transactional
    public Long grant(String resourceType, Long resourceId, String principalType, Long principalId, Long createdBy) {
        ResourceAclEntity existing = getOne(new LambdaQueryWrapper<ResourceAclEntity>()
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
        save(entity);
        grantService.invalidateAllGrants();
        return entity.getId();
    }

    @Override
    @Transactional
    public boolean revoke(Long aclId, String resourceType, Long resourceId) {
        boolean deleted = remove(new LambdaQueryWrapper<ResourceAclEntity>()
                .eq(ResourceAclEntity::getId, aclId)
                .eq(ResourceAclEntity::getResourceType, resourceType)
                .eq(ResourceAclEntity::getResourceId, resourceId));
        if (deleted) {
            grantService.invalidateAllGrants();
        }
        return deleted;
    }

    @Override
    public List<ResourceAclEntity> listByResource(String resourceType, Long resourceId) {
        return list(new LambdaQueryWrapper<ResourceAclEntity>()
                .eq(ResourceAclEntity::getResourceType, resourceType)
                .eq(ResourceAclEntity::getResourceId, resourceId)
                .orderByDesc(ResourceAclEntity::getCreatedAt));
    }

    @Override
    @Transactional
    public long deleteByResource(String resourceType, Long resourceId) {
        long deleted = baseMapper.delete(new LambdaQueryWrapper<ResourceAclEntity>()
                .eq(ResourceAclEntity::getResourceType, resourceType)
                .eq(ResourceAclEntity::getResourceId, resourceId));
        if (deleted > 0) {
            grantService.invalidateAllGrants();
        }
        return deleted;
    }
}
