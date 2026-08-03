package com.gj.llm.base.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.model.RoleCreateRequest;
import com.gj.llm.base.model.RoleUpdateRequest;

import java.util.List;
import java.util.Set;

/**
 * 角色服务接口 -- 角色增删改查、菜单分配，及供其他 Service 调用的角色查询能力。
 *
 * @author gj-llm
 */
public interface RoleService extends IService<RoleEntity> {

    /** 角色列表 */
    List<RoleEntity> listAll();

    /** 创建角色 */
    RoleEntity create(RoleCreateRequest request);

    /** 更新角色（name/description，code 不可改） */
    RoleEntity update(Long id, RoleUpdateRequest request);

    /** 删除角色（清理角色-菜单关联） */
    void delete(Long id);

    /** 为角色分配菜单（全量替换） */
    void assignMenus(Long roleId, Set<Long> menuIds);

    /** 按角色编码查询角色（如 USER） */
    RoleEntity getByCode(String code);

    /** 查询角色已分配的菜单 ID 列表 */
    List<Long> getRoleMenuIds(Long roleId);

    /** 删除菜单时，清理所有角色与该菜单的关联（供 MenuService 调用） */
    void removeMenuFromAllRoles(Long menuId);
}
