package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.entity.UserRoleEntity;
import com.gj.llm.base.event.UserChangedEvent;
import com.gj.llm.base.mapper.UserMapper;
import com.gj.llm.base.mapper.UserRoleMapper;
import com.gj.llm.base.model.UserCreateRequest;
import com.gj.llm.base.model.UserUpdateRequest;
import com.gj.llm.base.service.RoleService;
import com.gj.llm.base.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户服务实现 -- 通过 {@link UserMapper}、{@link UserRoleMapper} 管理用户与用户-角色关联；
 * 查询角色实体通过 {@link RoleService}，不直接依赖 RoleMapper。
 *
 * <p>用户变更（更新/重置密码/删除）时发布 {@link UserChangedEvent}，由安全用户服务在事务提交后失效缓存。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    private final UserRoleMapper userRoleMapper;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserServiceImpl(UserRoleMapper userRoleMapper, RoleService roleService,
                           PasswordEncoder passwordEncoder, ApplicationEventPublisher eventPublisher) {
        this.userRoleMapper = userRoleMapper;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<UserEntity> listAll() {
        List<UserEntity> users = list();
        users.forEach(user -> {
            user.setRoles(new HashSet<>(findRolesByUserId(user.getId())));
            user.setPassword(null);   // 不向外暴露密码密文
        });
        return users;
    }

    @Override
    public IPage<UserEntity> page(long pageNum, long size, String keyword) {
        Page<UserEntity> page = new Page<>(pageNum, size);
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.like(UserEntity::getUsername, kw)
                    .or().like(UserEntity::getNickname, kw);
        }
        wrapper.orderByDesc(UserEntity::getCreatedAt);

        IPage<UserEntity> result = page(page, wrapper);
        result.getRecords().forEach(user -> {
            user.setRoles(new HashSet<>(findRolesByUserId(user.getId())));
            user.setPassword(null);   // 不向外暴露密码密文
        });
        return result;
    }

    @Override
    public UserEntity getById(Long id) {
        UserEntity user = super.getById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + id);
        }
        user.setRoles(new HashSet<>(findRolesByUserId(id)));
        user.setPassword(null);   // 不向外暴露密码密文
        return user;
    }

    @Override
    @Transactional
    public UserEntity create(UserCreateRequest request) {
        long count = count(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, request.getUsername()));
        if (count > 0) {
            throw new RuntimeException("用户名已存在: " + request.getUsername());
        }

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .email(request.getEmail())
                .status(1)
                .build();

        save(user);
        log.info("创建用户成功: {}, id={}", user.getUsername(), user.getId());

        // 未指定角色时默认分配 USER 角色（通过 RoleService 查询）
        Set<Long> roleIds = request.getRoleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            RoleEntity defaultRole = roleService.getByCode("USER");
            if (defaultRole != null) {
                roleIds = Set.of(defaultRole.getId());
            }
        }
        if (roleIds != null && !roleIds.isEmpty()) {
            userRoleMapper.insertBatch(user.getId(), roleIds.stream().toList());
        }

        return getById(user.getId());
    }

    @Override
    @Transactional
    public UserEntity update(Long id, UserUpdateRequest request) {
        UserEntity user = super.getById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + id);
        }

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        updateById(user);
        log.info("更新用户成功: {}", user.getUsername());

        if (request.getRoleIds() != null) {
            userRoleMapper.deleteByUserId(id);
            if (!request.getRoleIds().isEmpty()) {
                userRoleMapper.insertBatch(id, request.getRoleIds().stream().toList());
            }
        }

        // 用户信息/角色变更，失效其安全用户缓存（事务提交后生效）
        eventPublisher.publishEvent(new UserChangedEvent(user.getUsername()));
        return getById(id);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        UserEntity user = super.getById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + id);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        updateById(user);
        log.info("重置密码成功: {}", user.getUsername());
        // 失效该用户缓存，强制重新认证
        eventPublisher.publishEvent(new UserChangedEvent(user.getUsername()));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        UserEntity user = super.getById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + id);
        }
        // 超级管理员（admin）受保护，禁止删除，确保系统始终存在管理员
        if ("admin".equals(user.getUsername())) {
            throw new RuntimeException("超级管理员不能删除");
        }
        userRoleMapper.deleteByUserId(id);
        removeById(id);
        log.info("删除用户成功: id={}", id);
        // 失效该用户缓存
        eventPublisher.publishEvent(new UserChangedEvent(user.getUsername()));
    }

    @Override
    public List<Long> getRoleIdsByUserId(Long userId) {
        return userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId))
                .stream().map(UserRoleEntity::getRoleId).toList();
    }

    @Override
    public UserEntity findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
    }

    /** 通过 RoleService 查询用户的角色实体集合 */
    private List<RoleEntity> findRolesByUserId(Long userId) {
        List<Long> roleIds = getRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleService.listByIds(roleIds);
    }
}
