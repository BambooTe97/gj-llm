package com.gj.llm.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.type.TypeReference;
import com.gj.llm.auth.config.AuthProperties;
import com.gj.llm.auth.entity.ResourceAclEntity;
import com.gj.llm.auth.mapper.ResourceAclMapper;
import com.gj.llm.auth.mapper.UserRoleMapper;
import com.gj.llm.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 授权判定原语 —— 用户→角色解析、资源授权集合查询与管理员旁路判定。
 *
 * <p>纯判定，不写库：授权写操作在 {@link ResourceAclService}，写后由其调用
 * {@link #invalidateAllGrants()} 失效缓存。所有结果带 Redis 缓存（TTL 兜底 +
 * 写后主动失效），避免聊天链路每次请求打库。</p>
 *
 * <p><b>红线</b>：本类不触碰 ThreadLocal（SecurityUtils / SecurityContextHolder），
 * userId 一律由调用方显式传入 —— 可在任意线程池线程安全调用。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrantService {

    private static final String ROLE_CACHE_PREFIX = "auth:roles:u";
    private static final String GRANT_CACHE_PREFIX = "auth:grants:u";
    /** 授权缓存统一前缀（写后按模式失效） */
    public static final String GRANT_CACHE_PATTERN = "auth:grants:*";

    private final UserRoleMapper userRoleMapper;
    private final ResourceAclMapper resourceAclMapper;
    private final RedisService redisService;
    private final AuthProperties authProperties;

    /**
     * 用户角色视图（角色 ID + 角色编码），内部缓存载体。
     */
    record UserRoleView(Set<Long> roleIds, List<String> roleCodes) {
    }

    /**
     * 用户的角色视图（ID 集 + 编码集），缓存 {@code auth:roles:u{uid}}。
     */
    public UserRoleView userRoles(Long userId) {
        if (userId == null) {
            return new UserRoleView(Set.of(), List.of());
        }
        String key = ROLE_CACHE_PREFIX + userId;
        UserRoleView cached = redisService.get(key, new TypeReference<UserRoleView>() {
        });
        if (cached != null) {
            return cached;
        }
        Set<Long> roleIds = Set.copyOf(safe(userRoleMapper.selectRoleIds(userId)));
        List<String> roleCodes = List.copyOf(safe(userRoleMapper.selectRoleCodes(userId)));
        UserRoleView view = new UserRoleView(roleIds, roleCodes);
        redisService.set(key, view, Duration.ofSeconds(authProperties.getCacheTtlSeconds()));
        return view;
    }

    /**
     * 用户被授权（主体=本人或其角色）的资源 ID 集合，缓存 {@code auth:grants:u{uid}:{type}}。
     */
    public Set<Long> grantedResourceIds(Long userId, String resourceType) {
        if (userId == null) {
            return Set.of();
        }
        String key = GRANT_CACHE_PREFIX + userId + ":" + resourceType;
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

    /**
     * 用户对指定资源是否有直接授权（本人或角色主体）。
     */
    public boolean hasGrant(Long userId, String resourceType, Long resourceId) {
        return grantedResourceIds(userId, resourceType).contains(resourceId);
    }

    /**
     * 用户是否持有管理员角色（全库可见可管旁路）。
     */
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRoles(userId).roleCodes().stream().anyMatch(authProperties::isAdminRole);
    }

    /**
     * 失效全部授权缓存（授权写后调用；角色主体授权影响面不可知，按模式全清）。
     *
     * @return 删除的缓存键数量
     */
    public long invalidateAllGrants() {
        return redisService.deleteByPattern(GRANT_CACHE_PATTERN);
    }

    private <T> List<T> safe(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
