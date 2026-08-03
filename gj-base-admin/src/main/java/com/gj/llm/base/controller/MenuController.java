package com.gj.llm.base.controller;

import com.gj.llm.base.entity.MenuEntity;
import com.gj.llm.base.model.MenuApiAssignRequest;
import com.gj.llm.base.model.MenuCreateRequest;
import com.gj.llm.base.model.MenuUpdateRequest;
import com.gj.llm.base.service.MenuService;
import com.gj.llm.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理控制器 -- 菜单树、增删改、菜单按钮关联接口配置。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET    /api/menus/tree          - 菜单树（管理用）</li>
 *   <li>POST   /api/menus               - 创建菜单</li>
 *   <li>PUT    /api/menus/{id}          - 更新菜单</li>
 *   <li>DELETE /api/menus/{id}          - 删除菜单</li>
 *   <li>GET    /api/menus/{id}/api-ids  - 菜单按钮关联的接口 ID</li>
 *   <li>PUT    /api/menus/{id}/apis     - 为菜单按钮分配接口</li>
 * </ul>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** 获取全量菜单树（管理用，含禁用/按钮） */
    @GetMapping("/tree")
    public ApiResponse<List<MenuEntity>> tree() {
        return ApiResponse.ok(menuService.getMenuTree());
    }

    /** 创建菜单 */
    @PostMapping
    public ApiResponse<MenuEntity> create(@Valid @RequestBody MenuCreateRequest request) {
        return ApiResponse.ok(menuService.create(request), "菜单创建成功");
    }

    /** 更新菜单 */
    @PutMapping("/{id}")
    public ApiResponse<MenuEntity> update(@PathVariable Long id,
                                          @Valid @RequestBody MenuUpdateRequest request) {
        return ApiResponse.ok(menuService.update(id, request), "菜单更新成功");
    }

    /** 删除菜单 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ApiResponse.ok(null, "菜单删除成功");
    }

    /** 查询菜单按钮关联的接口 ID 列表 */
    @GetMapping("/{id}/api-ids")
    public ApiResponse<List<Long>> menuApiIds(@PathVariable Long id) {
        return ApiResponse.ok(menuService.getMenuApiIds(id));
    }

    /** 为菜单按钮分配接口（全量替换） */
    @PutMapping("/{id}/apis")
    public ApiResponse<Void> assignApis(@PathVariable Long id,
                                        @Valid @RequestBody MenuApiAssignRequest request) {
        menuService.assignApis(id, request.getApiIds());
        return ApiResponse.ok(null, "接口分配成功");
    }
}
