package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.ResourceAclEntity;
import com.gj.llm.base.event.AclChangedEvent;
import com.gj.llm.base.mapper.ResourceAclMapper;
import com.gj.llm.base.service.ResourceAclService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 资源授权服务实现 -- 通过 {@link ResourceAclMapper} 管理 {@code resource_acl}。
 *
 * <p>每次写后发布 {@link AclChangedEvent}，由 {@code GrantServiceImpl} 在事务提交后
 * 失效 {@code auth:grants:*} 授权缓存（事务内直接删缓存会被并发读回填旧值）。</p>
 *
 * <p><b>红线</b>：不触碰 ThreadLocal，操作者 userId 由调用方显式传入。</p>
 *
 * @author gj-llm
 */
@Service
@RequiredArgsConstructor
public class ResourceAclServiceImpl extends ServiceImpl<ResourceAclMapper, ResourceAclEntity>
        implements ResourceAclService {

    private final ApplicationEventPublisher eventPublisher;

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
        eventPublisher.publishEvent(new AclChangedEvent());
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
            eventPublisher.publishEvent(new AclChangedEvent());
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
            eventPublisher.publishEvent(new AclChangedEvent());
        }
        return deleted;
    }
}
