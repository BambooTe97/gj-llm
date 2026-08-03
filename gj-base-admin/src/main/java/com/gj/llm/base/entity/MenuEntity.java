package com.gj.llm.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gj.llm.mybatis.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 菜单实体 -- 映射 {@code sys_menu} 表（MyBatis-Plus）。
 *
 * <p>菜单分三种类型：{@code M}=目录、{@code C}=菜单、{@code B}=按钮。
 * 通过 {@code parent_id} 自关联形成树形结构，{@code perms} 为权限标识，
 * 由角色经 {@link RoleMenuEntity}（{@code sys_role_menu}）关联。</p>
 *
 * <p>{@code children} 字段不映射数据库列，由 Service 层构建菜单树时填充。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class MenuEntity extends BaseEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父菜单 ID，0=顶层 */
    private Long parentId;

    /** 菜单名称（展示用） */
    private String name;

    /**
     * 菜单类型：{@code M}=目录、{@code C}=菜单、{@code B}=按钮。
     */
    private String type;

    /** 路由路径（按钮可为空） */
    private String path;

    /** 前端组件路径（相对 views，如 system/user/UserManage；目录/按钮可空） */
    private String component;

    /** 权限标识（如 system:user:list） */
    private String perms;

    /** 图标名称（Element Plus 图标组件名） */
    private String icon;

    /** 排序（升序） */
    private Integer sort;

    /** 是否显示：1=显示，0=隐藏（隐藏仍注册路由） */
    @Builder.Default
    private Integer visible = 1;

    /** 状态：1=启用，0=禁用 */
    @Builder.Default
    private Integer status = 1;

    /**
     * 子菜单集合 -- 不映射数据库字段，由 Service 层构建菜单树时填充。
     */
    @TableField(exist = false)
    private List<MenuEntity> children;
}
