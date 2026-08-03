package com.gj.llm.base.controller;

import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.model.RoleCreateRequest;
import com.gj.llm.base.model.RoleMenuAssignRequest;
import com.gj.llm.base.model.RoleUpdateRequest;
import com.gj.llm.base.service.RoleService;
import com.gj.llm.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器 -- 角色增删改查、菜单分配。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET    /api/roles              - 角色列表</li>
 *   <li>POST   /api/roles              - 创建角色</li>
 *   <li>PUT    /api/roles/{id}         - 更新角色</li>
 *   <li>DELETE /api/roles/{id}         - 删除角色</li>
 *   <li>GET    /api/roles/{id}/menu-ids - 角色已分配菜单 ID</li>
 *   <li>PUT    /api/roles/{id}/menus   - 为角色分配菜单</li>
 * </ul>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /** 获取角色列表 */
    @GetMapping
    public ApiResponse<List<RoleEntity>> list() {
        return ApiResponse.ok(roleService.listAll());
    }

    /** 创建角色 */
    @PostMapping
    public ApiResponse<RoleEntity> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.ok(roleService.create(request), "角色创建成功");
    }

    /** 更新角色 */
    @PutMapping("/{id}")
    public ApiResponse<RoleEntity> update(@PathVariable Long id,
                                          @Valid @RequestBody RoleUpdateRequest request) {
        return ApiResponse.ok(roleService.update(id, request), "角色更新成功");
    }

    /** 删除角色 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok(null, "角色删除成功");
    }

    /** 查询角色已分配的菜单 ID 列表 */
    @GetMapping("/{id}/menu-ids")
    public ApiResponse<List<Long>> roleMenuIds(@PathVariable Long id) {
        return ApiResponse.ok(roleService.getRoleMenuIds(id));
    }

    /** 为角色分配菜单（全量替换） */
    @PutMapping("/{id}/menus")
    public ApiResponse<Void> assignMenus(@PathVariable Long id,
                                         @Valid @RequestBody RoleMenuAssignRequest request) {
        roleService.assignMenus(id, request.getMenuIds());
        return ApiResponse.ok(null, "菜单分配成功");
    }
}
