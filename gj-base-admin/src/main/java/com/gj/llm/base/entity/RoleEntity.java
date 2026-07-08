package com.gj.llm.base.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色实体 —— 映射 {@code sys_role} 表（MyBatis-Plus）。
 *
 * <p>角色用于权限控制，如 ADMIN、USER 等。
 * 通过 {@code sys_user_role} 中间表与 {@link UserEntity} 建立多对多关联。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role")
public class RoleEntity {

    /** 主键（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色名称（展示用），例如 "系统管理员" */
    private String name;

    /**
     * 角色编码（权限判断用），例如 {@code ADMIN}、{@code USER}。
     * 对应 Spring Security 的 {@code ROLE_ADMIN}、{@code ROLE_USER}。
     */
    private String code;

    /** 角色描述 */
    private String description;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
