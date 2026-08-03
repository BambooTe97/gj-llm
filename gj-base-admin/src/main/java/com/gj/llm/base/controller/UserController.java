package com.gj.llm.base.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.model.ResetPasswordRequest;
import com.gj.llm.base.model.UserCreateRequest;
import com.gj.llm.base.model.UserUpdateRequest;
import com.gj.llm.base.service.UserService;
import com.gj.llm.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器 -- 用户分页查询、增删改、重置密码。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET    /api/users              - 用户分页列表（?page&size&keyword）</li>
 *   <li>GET    /api/users/{id}         - 用户详情</li>
 *   <li>POST   /api/users              - 创建用户</li>
 *   <li>PUT    /api/users/{id}         - 更新用户</li>
 *   <li>PUT    /api/users/{id}/password - 重置密码</li>
 *   <li>DELETE /api/users/{id}         - 删除用户</li>
 * </ul>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 分页查询用户（支持用户名/昵称模糊搜索） */
    @GetMapping
    public ApiResponse<IPage<UserEntity>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userService.page(page, size, keyword));
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

    /** 重置密码 */
    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.getNewPassword());
        return ApiResponse.ok(null, "密码重置成功");
    }

    /** 删除用户 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok(null, "用户删除成功");
    }
}
