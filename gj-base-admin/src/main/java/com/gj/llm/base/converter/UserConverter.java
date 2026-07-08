package com.gj.llm.base.converter;

import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.security.model.SecurityUser;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户对象转换器 —— {@link UserEntity} 与 {@link SecurityUser} 之间的互转。
 *
 * <p>SecurityUser 是安全模块的通用用户对象，UserEntity 是数据库实体。
 * 通过此类完成两者的转换，使业务层与安全层解耦。</p>
 *
 * @author gj-llm
 */
public final class UserConverter {

    private UserConverter() {
        // 工具类禁止实例化
    }

    /**
     * 将数据库实体转为安全模块的通用用户对象。
     *
     * @param entity 数据库用户实体
     * @return 安全用户对象
     */
    public static SecurityUser toSecurityUser(UserEntity entity) {
        Set<RoleEntity> roles = entity.getRoles();
        List<String> roleCodes = (roles != null)
                ? roles.stream().map(RoleEntity::getCode).collect(Collectors.toList())
                : List.of();

        return new SecurityUser(
                entity.getId(),
                entity.getUsername(),
                entity.getPassword(),
                entity.getNickname(),
                entity.getAvatar(),
                entity.getStatus() == 1,      // 1=启用，其他=禁用
                roleCodes
        );
    }
}
