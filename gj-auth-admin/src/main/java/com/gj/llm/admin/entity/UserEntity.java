package com.gj.llm.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 用户实体 —— 映射 {@code sys_user} 表（MyBatis-Plus）。
 *
 * <p>与 {@link RoleEntity} 为多对多关系，通过中间表 {@code sys_user_role} 关联。
 * {@code roles} 字段不映射数据库列，由 Service 层手动加载。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class UserEntity {

    /** 主键（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名（登录凭证），唯一 */
    private String username;

    /** 密码（BCrypt 密文） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 邮箱 */
    private String email;

    /**
     * 账户状态。
     * 1 = 启用，0 = 禁用。
     */
    @Builder.Default
    private Integer status = 1;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（插入和更新时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 关联的角色集合 —— 不映射数据库字段，由 Service 手动加载。
     */
    @TableField(exist = false)
    private Set<RoleEntity> roles;
}
