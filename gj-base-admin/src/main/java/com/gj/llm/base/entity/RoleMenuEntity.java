package com.gj.llm.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色-菜单关联实体 -- 映射 {@code sys_role_menu} 中间表（MyBatis-Plus）。
 *
 * <p>关联 {@link RoleEntity} 与 {@link MenuEntity} 的多对多关系，
 * 用于角色分配菜单（含按钮权限）。</p>
 *
 * @author gj-llm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role_menu")
public class RoleMenuEntity {

    /** 角色 ID */
    private Long roleId;

    /** 菜单 ID */
    private Long menuId;
}
