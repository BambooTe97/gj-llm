package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.entity.UserRoleEntity;
import com.gj.llm.base.mapper.RoleMapper;
import com.gj.llm.base.mapper.UserMapper;
import com.gj.llm.base.mapper.UserRoleMapper;
import com.gj.llm.base.model.UserCreateRequest;
import com.gj.llm.base.model.UserUpdateRequest;
import com.gj.llm.base.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(RoleMapper roleMapper, UserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<UserEntity> listAll() {
        List<UserEntity> users = list();
        users.forEach(user -> {
            List<RoleEntity> roles = findRolesByUserId(user.getId());
            user.setRoles(new HashSet<>(roles));
        });
        return users;
    }

    @Override
    public UserEntity getById(Long id) {
        UserEntity user = super.getById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + id);
        }
        List<RoleEntity> roles = findRolesByUserId(id);
        user.setRoles(new HashSet<>(roles));
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

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            userRoleMapper.insertBatch(user.getId(), request.getRoleIds().stream().toList());
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

        return getById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (super.getById(id) == null) {
            throw new RuntimeException("用户不存在: id=" + id);
        }
        userRoleMapper.deleteByUserId(id);
        removeById(id);
        log.info("删除用户成功: id={}", id);
    }

    private List<RoleEntity> findRolesByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId))
                .stream().map(UserRoleEntity::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>().in(RoleEntity::getId, roleIds));
    }
}
