package com.gj.llm.auth.service;

import com.gj.llm.auth.mapper.UserRoleMapper;
import com.gj.llm.auth.mapper.UserRoleMapper.PrincipalOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 主体信息查询 —— 共享面板的用户/角色选择器与授权列表的展示名解析。
 *
 * <p>对 sys_user / sys_role 只读；展示名规则：用户取昵称兜底登录名，角色取名称。</p>
 *
 * <p><b>红线</b>：不触碰 ThreadLocal，userId 由调用方显式传入。</p>
 *
 * @author gj-llm
 */
@Service
@RequiredArgsConstructor
public class PrincipalLookupService {

    private final UserRoleMapper userRoleMapper;

    /**
     * 用户展示名：昵称优先，登录名兜底，均无返回 {@code "用户#id"}。
     */
    public String userDisplayName(Long userId) {
        if (userId == null) {
            return null;
        }
        String nickname = userRoleMapper.selectNickname(userId);
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        String username = userRoleMapper.selectUsername(userId);
        if (username != null && !username.isBlank()) {
            return username;
        }
        return "用户#" + userId;
    }

    /**
     * 角色展示名，不存在返回 {@code "角色#id"}
     */
    public String roleDisplayName(Long roleId) {
        if (roleId == null) {
            return null;
        }
        String name = userRoleMapper.selectRoleName(roleId);
        return name == null || name.isBlank() ? "角色#" + roleId : name;
    }

    /**
     * 用户是否存在（授权前校验）。
     */
    public boolean userExists(Long userId) {
        return userId != null && userRoleMapper.countUser(userId) > 0;
    }

    /**
     * 角色是否存在（授权前校验）。
     */
    public boolean roleExists(Long roleId) {
        return roleId != null && userRoleMapper.countRole(roleId) > 0;
    }

    /**
     * 按关键词搜索启用用户（共享面板选择器）。
     */
    public List<PrincipalOption> searchUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return userRoleMapper.searchUsers(keyword.trim());
    }

    /**
     * 全量角色列表（共享面板选择器）。
     */
    public List<PrincipalOption> listRoles() {
        return userRoleMapper.listRoles();
    }
}
