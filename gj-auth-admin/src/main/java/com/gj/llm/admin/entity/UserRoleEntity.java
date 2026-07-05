package com.gj.llm.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户-角色关联实体 —— 映射 {@code sys_user_role} 中间表（MyBatis-Plus）。
 *
 * <p>关联 {@link UserEntity} 与 {@link RoleEntity} 的多对多关系。</p>
 *
 * @author gj-llm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_role")
public class UserRoleEntity {

    /** 用户 ID */
    private Long userId;

    /** 角色 ID */
    private Long roleId;
}
