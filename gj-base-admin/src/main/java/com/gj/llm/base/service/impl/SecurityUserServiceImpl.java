package com.gj.llm.base.service.impl;

import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.event.RoleChangedEvent;
import com.gj.llm.base.event.UserChangedEvent;
import com.gj.llm.base.init.ApiPermissionChangedEvent;
import com.gj.llm.base.model.SecurityUserCacheData;
import com.gj.llm.base.service.MenuService;
import com.gj.llm.base.service.RoleService;
import com.gj.llm.base.service.UserService;
import com.gj.llm.redis.constant.CacheConstants;
import com.gj.llm.redis.service.RedisService;
import com.gj.llm.security.model.SecurityUser;
import com.gj.llm.security.service.SecurityUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * 安全用户服务实现 -- 通过 {@link UserService}/{@link RoleService}/{@link MenuService}
 * 聚合用户、角色、权限标识，构造 {@link SecurityUser} 供认证层使用。
 *
 * <p>不直接依赖任何 Mapper，所有数据均通过对应 Service 获取，遵循分层规范。</p>
 *
 * <h3>Redis 缓存</h3>
 * <p>认证过滤器每次请求都会调用 {@link #findByUsername}，为避免反复查库，将聚合结果以
 * {@link SecurityUserCacheData} 形式缓存到 Redis（key={@code login:user:{username}}，默认 30 分钟 TTL）。
 * 用户/角色/菜单权限变更时通过事件失效（事务提交后），保证缓存与 DB 一致。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityUserServiceImpl implements SecurityUserService {

    private final UserService userService;
    private final RoleService roleService;
    private final MenuService menuService;
    private final RedisService redisService;

    @Override
    public Optional<SecurityUser> findByUsername(String username) {
        // 1. 先查 Redis 缓存
        String key = CacheConstants.LOGIN_USER_KEY + username;
        SecurityUserCacheData cached = redisService.get(key, SecurityUserCacheData.class);
        if (cached != null) {
            log.debug("命中安全用户缓存: {}", username);
            return Optional.of(cached.toSecurityUser());
        }

        // 2. 缓存未命中 -> 查库聚合
        UserEntity userEntity = userService.findByUsername(username);
        if (userEntity == null) {
            log.debug("用户不存在: {}", username);
            return Optional.empty();
        }
        // 通过 RoleService 查询角色实体（角色 ID 来自 UserService）
        List<RoleEntity> roles = roleService.listByIds(userService.getRoleIdsByUserId(userEntity.getId()));
        userEntity.setRoles(new HashSet<>(roles));
        // 通过 MenuService 查询细粒度权限标识（由角色关联菜单得出）
        List<String> permissions = menuService.getPermsByUserId(userEntity.getId());

        // 3. 构造缓存数据并写入 Redis（TTL 兜底，防止事件失效遗漏）
        List<String> roleCodes = roles.stream().map(RoleEntity::getCode).toList();
        SecurityUserCacheData cacheData = new SecurityUserCacheData(
                userEntity.getId(),
                userEntity.getUsername(),
                userEntity.getPassword(),
                userEntity.getNickname(),
                userEntity.getAvatar(),
                userEntity.getStatus() == 1,
                roleCodes,
                permissions);
        redisService.set(key, cacheData, Duration.ofMinutes(CacheConstants.LOGIN_USER_TTL_MINUTES));

        log.debug("用户查询成功: {}, 权限数: {}", username, permissions.size());
        return Optional.of(cacheData.toSecurityUser());
    }

    // ==================== 缓存失效 ====================

    /**
     * 失效单个用户缓存。
     */
    public void evict(String username) {
        redisService.delete(CacheConstants.LOGIN_USER_KEY + username);
    }

    /**
     * 失效全部用户缓存（角色/菜单权限变更可能影响所有用户）。
     */
    public void evictAll() {
        long n = redisService.deleteByPattern(CacheConstants.LOGIN_USER_KEY + "*");
        log.info("清除全部安全用户缓存: {} 条", n);
    }

    /**
     * 用户变更（更新/重置密码/删除）-> 事务提交后失效该用户缓存。
     *
     * <p>用 {@code AFTER_COMMIT} 阶段：避免事务回滚后误清，且提交后清可覆盖提交瞬间并发读回写的旧值。
     * {@code fallbackExecution=true} 保证非事务上下文下也能立即执行。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserChanged(UserChangedEvent event) {
        evict(event.username());
    }

    /**
     * 角色变更（更新/删除/分配菜单）-> 事务提交后失效全部用户缓存。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoleChanged(RoleChangedEvent event) {
        evictAll();
    }

    /**
     * 菜单/接口权限变更 -> 事务提交后失效全部用户缓存（权限标识可能变化）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPermissionChanged(ApiPermissionChangedEvent event) {
        evictAll();
    }
}
