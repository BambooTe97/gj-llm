package com.gj.llm.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单按钮-接口关联实体 -- 映射 {@code sys_menu_api} 中间表（MyBatis-Plus）。
 *
 * <p>建立权限点（菜单按钮）与接口的多对多关系：角色分配菜单按钮后，
 * 即可访问该按钮关联的所有接口。由 {@code ApiAutoLinker} 在启动时按约定建立默认关联，
 * admin 可在菜单管理页调整。</p>
 *
 * @author gj-llm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_menu_api")
public class MenuApiEntity {

    /** 菜单/按钮 ID（权限点） */
    private Long menuId;

    /** 接口 ID */
    private Long apiId;
}
