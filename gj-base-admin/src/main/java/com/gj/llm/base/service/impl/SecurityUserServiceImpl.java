package com.gj.llm.base.service.impl;

import com.gj.llm.base.converter.UserConverter;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.mapper.RoleMapper;
import com.gj.llm.base.mapper.UserMapper;
import com.gj.llm.security.model.SecurityUser;
import com.gj.llm.security.service.SecurityUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 安全用户服务实现 —— 实现 gj-security 模块定义的 {@link SecurityUserService} 接口。
 *
 * <p>通过 MyBatis-Plus Mapper 从 MySQL 加载用户信息，
 * 使用 {@link UserConverter} 转换为安全模块的 {@link SecurityUser} 对象。
 * 这是 gj-security（安全）与 gj-core-admin（业务）之间的桥梁。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityUserServiceImpl implements SecurityUserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    /**
     * 根据用户名从数据库查询用户，并转换角色信息为 SecurityUser。
     *
     * @param username 用户名
     * @return SecurityUser，不存在则 empty
     */
    @Override
    public Optional<SecurityUser> findByUsername(String username) {
        UserEntity userEntity = userMapper.selectByUsername(username);
        if (userEntity != null) {
            // 加载用户关联的角色
            List<RoleEntity> roles = roleMapper.selectByUserId(userEntity.getId());
            userEntity.setRoles(new java.util.HashSet<>(roles));

            log.debug("用户查询成功: {}", username);
            return Optional.of(UserConverter.toSecurityUser(userEntity));
        }
        log.debug("用户不存在: {}", username);
        return Optional.empty();
    }
}
