package com.gj.llm.base.model;

import com.gj.llm.base.entity.MenuEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前登录用户信息响应 DTO -- 供前端获取用户资料、菜单树、权限标识。
 *
 * <p>登录后及页面刷新时由 {@code GET /api/auth/userinfo} 返回，
 * 前端据此动态注册路由、渲染导航、做按钮级权限控制。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 角色编码列表（如 ADMIN、USER） */
    private List<String> roles;

    /** 权限标识列表（如 system:user:list） */
    private List<String> permissions;

    /** 当前用户可访问的菜单树（仅目录/菜单类型） */
    private List<MenuEntity> menus;
}
