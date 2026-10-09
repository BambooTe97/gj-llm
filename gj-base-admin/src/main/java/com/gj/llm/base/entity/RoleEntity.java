package com.gj.llm.base.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 数据域：全部数据 */
    public static final int DATA_SCOPE_ALL = 1;
    /** 数据域：自定义部门（可见集合存 sys_role_dept） */
    public static final int DATA_SCOPE_CUSTOM = 2;
    /** 数据域：本部门 */
    public static final int DATA_SCOPE_DEPT = 3;
    /** 数据域：本部门及以下 */
    public static final int DATA_SCOPE_DEPT_AND_CHILD = 4;
    /** 数据域：仅本人 */
    public static final int DATA_SCOPE_SELF = 5;

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
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

    /**
     * 数据权限域（五档）：1=全部 2=自定义部门 3=本部门 4=本部门及以下 5=仅本人。
     * DB 默认 1（全部），存量角色零回归；解析逻辑见 {@code DataScopeServiceImpl}。
     */
    private Integer dataScope;

    /** 自定义部门 ID 集合（仅 dataScope=2 生效；非表字段，经 create/update 请求透传） */
    @TableField(exist = false)
    private List<Long> deptIds;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
