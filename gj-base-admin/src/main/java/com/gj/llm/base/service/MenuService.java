package com.gj.llm.base.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.MenuApiEntity;
import com.gj.llm.base.entity.MenuEntity;
import com.gj.llm.base.model.MenuCreateRequest;
import com.gj.llm.base.model.MenuUpdateRequest;

import java.util.List;
import java.util.Set;

/**
 * 菜单服务接口 -- 菜单树构建、CRUD、菜单-接口关联，及供其他 Service/组件调用的查询能力。
 *
 * @author gj-llm
 */
public interface MenuService extends IService<MenuEntity> {

    /** 获取全量菜单树（管理用，含禁用/按钮） */
    List<MenuEntity> getMenuTree();

    /** 获取当前登录用户的菜单树（仅目录/菜单类型且显示，前端动态路由用） */
    List<MenuEntity> getCurrentUserMenuTree();

    /** 创建菜单 */
    MenuEntity create(MenuCreateRequest request);

    /** 更新菜单（仅更新非空字段） */
    MenuEntity update(Long id, MenuUpdateRequest request);

    /** 删除菜单（校验无子节点，通过 RoleService 清理角色关联，清理接口关联） */
    void delete(Long id);

    /** 查询菜单按钮关联的接口 ID 列表 */
    List<Long> getMenuApiIds(Long menuId);

    /** 为菜单按钮分配接口（全量替换，发布权限变更事件） */
    void assignApis(Long menuId, Set<Long> apiIds);

    /** 按权限点查询菜单按钮（供接口自动关联） */
    MenuEntity getByPerms(String perms);

    /** 查询用户的权限标识列表（供认证加载） */
    List<String> getPermsByUserId(Long userId);

    /** 查询全部菜单-接口关联（供权限缓存构建） */
    List<MenuApiEntity> listAllMenuApiLinks();

    /** 查询所有有权限标识的菜单（供权限缓存构建） */
    List<MenuEntity> listMenusWithPerms();

    /** 批量插入菜单-接口关联（供接口自动关联） */
    void addApiLinks(List<MenuApiEntity> links);
}
