package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.converter.UserConverter;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.entity.UserRoleEntity;
import com.gj.llm.base.mapper.RoleMapper;
import com.gj.llm.base.mapper.UserMapper;
import com.gj.llm.base.mapper.UserRoleMapper;
import com.gj.llm.security.model.SecurityUser;
import com.gj.llm.security.service.SecurityUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SecurityUserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements SecurityUserService {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    public SecurityUserServiceImpl(RoleMapper roleMapper, UserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public Optional<SecurityUser> findByUsername(String username) {
        UserEntity userEntity = getOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        if (userEntity != null) {
            List<RoleEntity> roles = findRolesByUserId(userEntity.getId());
            userEntity.setRoles(new HashSet<>(roles));
            log.debug("用户查询成功: {}", username);
            return Optional.of(UserConverter.toSecurityUser(userEntity));
        }
        log.debug("用户不存在: {}", username);
        return Optional.empty();
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
