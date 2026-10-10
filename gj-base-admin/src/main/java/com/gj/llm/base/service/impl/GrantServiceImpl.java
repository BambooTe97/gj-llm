package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.base.entity.ResourceAclEntity;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.event.AclChangedEvent;
import com.gj.llm.base.mapper.ResourceAclMapper;
import com.gj.llm.base.service.GrantService;
import com.gj.llm.base.service.RoleService;
import com.gj.llm.base.service.UserService;
import com.gj.llm.redis.constant.CacheConstants;
import com.gj.llm.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 授权判定服务实现 —— 用户角色解析走 {@link UserService}/{@link RoleService}，
 * 授权集合查 {@code resource_acl}，结果统一 Redis 缓存。
 *
 * <p><b>红线</b>：不触碰 ThreadLocal，userId 由调用方显式传入。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrantServiceImpl implements GrantService {

    private final UserService userService;
    private final RoleService roleService;
    private final ResourceAclMapper resourceAclMapper;
    private final RedisService redisService;
    private final AuthProperties authProperties;

    /**
     * 用户角色视图（角色 ID + 角色编码），内部缓存载体。
     */
    private record UserRoleView(Set<Long> roleIds, List<String> roleCodes) {
    }

    /**
     * 用户角色视图（ID 集 + 编码集），缓存 {@code auth:roles:u{uid}}。
     */
    private UserRoleView userRoles(Long userId) {
        if (userId == null) {
            return new UserRoleView(Set.of(), List.of());
        }
        String key = CacheConstants.AUTH_ROLES_KEY + userId;
        UserRoleView cached = redisService.get(key, new TypeReference<UserRoleView>() {
        });
        if (cached != null) {
            return cached;
        }
        List<Long> roleIdList = userService.getRoleIdsByUserId(userId);
        Set<Long> roleIds = roleIdList == null ? Set.of() : Set.copyOf(roleIdList);
        List<String> roleCodes = roleIds.isEmpty() ? List.of()
                : roleService.listByIds(roleIds).stream().map(RoleEntity::getCode).toList();
        UserRoleView view = new UserRoleView(roleIds, roleCodes);
        redisService.set(key, view, Duration.ofSeconds(authProperties.getCacheTtlSeconds()));
        return view;
    }

    @Override
    public Set<Long> grantedResourceIds(Long userId, String resourceType) {
        if (userId == null) {
            return Set.of();
        }
        String key = CacheConstants.AUTH_GRANTS_KEY + userId + ":" + resourceType;
        Set<Long> cached = redisService.get(key, new TypeReference<Set<Long>>() {
        });
        if (cached != null) {
            return cached;
        }
        // 查询本人授权 + 角色授权两类主体
        Set<Long> roleIds = userRoles(userId).roleIds();
        LambdaQueryWrapper<ResourceAclEntity> wrapper = new LambdaQueryWrapper<ResourceAclEntity>()
                .select(ResourceAclEntity::getResourceId)
                .eq(ResourceAclEntity::getResourceType, resourceType)
                .and(w -> {
                    w.eq(ResourceAclEntity::getPrincipalType, AuthProperties.PRINCIPAL_USER)
                            .eq(ResourceAclEntity::getPrincipalId, userId);
                    if (!roleIds.isEmpty()) {
                        w.or(sub -> sub.eq(ResourceAclEntity::getPrincipalType, AuthProperties.PRINCIPAL_ROLE)
                                .in(ResourceAclEntity::getPrincipalId, roleIds));
                    }
                });
        Set<Long> result = Set.copyOf(resourceAclMapper.selectObjs(wrapper).stream()
                .filter(Objects::nonNull)
                .map(o -> (Long) o)
                .toList());
        redisService.set(key, result, Duration.ofSeconds(authProperties.getCacheTtlSeconds()));
        return result;
    }

    @Override
    public boolean hasGrant(Long userId, String resourceType, Long resourceId) {
        return grantedResourceIds(userId, resourceType).contains(resourceId);
    }

    @Override
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRoles(userId).roleCodes().stream().anyMatch(authProperties::isAdminRole);
    }

    @Override
    public long invalidateAllGrants() {
        return redisService.deleteByPattern(CacheConstants.AUTH_GRANTS_PATTERN);
    }

    /**
     * 授权（ACL）变更 -> 事务提交后失效全部授权缓存。
     *
     * <p>事务内直接删缓存存在"已删缓存、未提交"间隙被并发读回填旧值的竞态
     * （脏值可活满整个 TTL），与用户/角色缓存失效同走 AFTER_COMMIT 口径。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAclChanged(AclChangedEvent event) {
        long n = invalidateAllGrants();
        log.info("授权变更，事务提交后失效授权缓存: {} 条", n);
    }
}
