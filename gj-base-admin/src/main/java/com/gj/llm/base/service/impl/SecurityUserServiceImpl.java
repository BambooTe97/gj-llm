package com.gj.llm.base.service.impl;

import com.gj.llm.base.converter.UserConverter;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.service.MenuService;
import com.gj.llm.base.service.RoleService;
import com.gj.llm.base.service.UserService;
import com.gj.llm.security.model.SecurityUser;
import com.gj.llm.security.service.SecurityUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * 安全用户服务实现 -- 通过 {@link UserService}/{@link RoleService}/{@link MenuService}
 * 聚合用户、角色、权限标识，构造 {@link SecurityUser} 供认证层使用。
 *
 * <p>不直接依赖任何 Mapper，所有数据均通过对应 Service 获取，遵循分层规范。</p>
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

    @Override
    public Optional<SecurityUser> findByUsername(String username) {
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
        log.debug("用户查询成功: {}, 权限数: {}", username, permissions.size());
        return Optional.of(UserConverter.toSecurityUser(userEntity, permissions));
    }
}
