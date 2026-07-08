package com.gj.llm.base.controller;

import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.model.ApiResponse;
import com.gj.llm.base.model.UserCreateRequest;
import com.gj.llm.base.model.UserUpdateRequest;
import com.gj.llm.base.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器 —— 提供用户的增删改查 REST API。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET    /api/users     — 用户列表</li>
 *   <li>GET    /api/users/{id} — 用户详情</li>
 *   <li>POST   /api/users     — 创建用户</li>
 *   <li>PUT    /api/users/{id} — 更新用户</li>
 *   <li>DELETE /api/users/{id} — 删除用户</li>
 * </ul>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 获取用户列表 */
    @GetMapping
    public ApiResponse<List<UserEntity>> list() {
        return ApiResponse.ok(userService.listAll());
    }

    /** 获取用户详情 */
    @GetMapping("/{id}")
    public ApiResponse<UserEntity> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    /** 创建用户 */
    @PostMapping
    public ApiResponse<UserEntity> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(userService.create(request), "用户创建成功");
    }

    /** 更新用户 */
    @PutMapping("/{id}")
    public ApiResponse<UserEntity> update(@PathVariable Long id,
                                           @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userService.update(id, request), "用户更新成功");
    }

    /** 删除用户 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok(null, "用户删除成功");
    }
}
