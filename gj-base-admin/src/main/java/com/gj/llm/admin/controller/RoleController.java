package com.gj.llm.admin.controller;

import com.gj.llm.admin.entity.RoleEntity;
import com.gj.llm.admin.model.ApiResponse;
import com.gj.llm.admin.model.RoleCreateRequest;
import com.gj.llm.admin.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器 —— 提供角色的增删查 REST API。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET    /api/roles     — 角色列表</li>
 *   <li>POST   /api/roles     — 创建角色</li>
 *   <li>DELETE /api/roles/{id} — 删除角色</li>
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

    /** 删除角色 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok(null, "角色删除成功");
    }
}
