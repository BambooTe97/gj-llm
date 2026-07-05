package com.gj.llm.admin.service.impl;

import com.gj.llm.admin.entity.RoleEntity;
import com.gj.llm.admin.entity.UserEntity;
import com.gj.llm.admin.mapper.RoleMapper;
import com.gj.llm.admin.mapper.UserMapper;
import com.gj.llm.admin.mapper.UserRoleMapper;
import com.gj.llm.admin.model.UserCreateRequest;
import com.gj.llm.admin.model.UserUpdateRequest;
import com.gj.llm.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/**
 * 用户管理服务实现 —— 基于 MyBatis-Plus，提供用户的增删改查操作。
 *
 * <p>创建用户时自动使用 BCrypt 加密密码；更新用户时不会修改密码；
 * 角色关联通过 {@link UserRoleMapper} 管理（先删后插策略）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<UserEntity> listAll() {
        List<UserEntity> users = userMapper.selectList(null);
        // 为每个用户加载角色
        users.forEach(user -> {
            List<RoleEntity> roles = roleMapper.selectByUserId(user.getId());
            user.setRoles(new HashSet<>(roles));
        });
        return users;
    }

    @Override
    public UserEntity getById(Long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + id);
        }
        // 加载角色
        List<RoleEntity> roles = roleMapper.selectByUserId(id);
        user.setRoles(new HashSet<>(roles));
        return user;
    }

    @Override
    @Transactional
    public UserEntity create(UserCreateRequest request) {
        // 用户名唯一性检查
        if (userMapper.countByUsername(request.getUsername()) > 0) {
            throw new RuntimeException("用户名已存在: " + request.getUsername());
        }

        // 构造用户实体（密码 BCrypt 加密）
        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .email(request.getEmail())
                .status(1)   // 默认启用
                .build();

        userMapper.insert(user);
        log.info("创建用户成功: {}, id={}", user.getUsername(), user.getId());

        // 关联角色
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            userRoleMapper.insertBatch(user.getId(), request.getRoleIds().stream().toList());
        }

        // 返回带角色的用户
        return getById(user.getId());
    }

    @Override
    @Transactional
    public UserEntity update(Long id, UserUpdateRequest request) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + id);
        }

        // 更新允许修改的字段
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        userMapper.updateById(user);
        log.info("更新用户成功: {}", user.getUsername());

        // 更新角色（全量替换：先删后插）
        if (request.getRoleIds() != null) {
            userRoleMapper.deleteByUserId(id);
            if (!request.getRoleIds().isEmpty()) {
                userRoleMapper.insertBatch(id, request.getRoleIds().stream().toList());
            }
        }

        // 返回带角色的用户
        return getById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new RuntimeException("用户不存在: id=" + id);
        }
        // 先删角色关联，再删用户
        userRoleMapper.deleteByUserId(id);
        userMapper.deleteById(id);
        log.info("删除用户成功: id={}", id);
    }
}
