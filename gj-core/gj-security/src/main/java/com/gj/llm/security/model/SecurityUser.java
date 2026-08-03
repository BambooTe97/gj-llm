package com.gj.llm.security.model;

import com.gj.llm.common.util.UserIdProvider;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 安全模块通用用户对象 —— 实现 Spring Security 的 {@link UserDetails} 接口。
 *
 * <p>该对象由业务模块（如 gj-core-admin）在实现 {@code SecurityUserService} 时构造，
 * 作为认证层与业务用户实体之间的桥梁，避免安全模块直接依赖数据库实体。</p>
 *
 * @author gj-llm
 */
@Getter
public class SecurityUser implements UserDetails, UserIdProvider {

    /** 用户 ID（数据库主键） */
    private final Long userId;

    /** 用户名（登录凭证） */
    private final String username;

    /** 密码（BCrypt 密文） */
    private final String password;

    /** 昵称 */
    private final String nickname;

    /** 头像 URL */
    private final String avatar;

    /** 账户状态：true=启用，false=禁用 */
    private final boolean enabled;

    /** 权限列表（角色 ROLE_&lt;code&gt; + 细粒度权限标识 perms） */
    private final List<GrantedAuthority> authorities;

    /** 细粒度权限标识列表（如 system:user:list），由业务模块根据角色关联菜单加载 */
    private final List<String> permissions;

    /**
     * 构造安全用户对象。
     *
     * <p>{@code authorities} 同时包含角色（{@code ROLE_<code>} 前缀，用于 {@code hasRole}）
     * 与细粒度权限标识（perms，用于 {@code hasAuthority}），使 {@code @PreAuthorize} 可按
     * 菜单/按钮级权限控制接口访问。</p>
     *
     * @param roleCodes    角色编码列表，转为 {@code ROLE_<code>} 权限
     * @param permissions  细粒度权限标识列表，直接作为权限加入 authorities
     */
    public SecurityUser(Long userId, String username, String password,
                        String nickname, String avatar,
                        boolean enabled, List<String> roleCodes, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.avatar = avatar;
        this.enabled = enabled;
        this.permissions = permissions != null ? permissions : Collections.emptyList();

        // authorities = 角色（ROLE_ 前缀）+ 细粒度权限标识（perms）
        List<GrantedAuthority> all = new ArrayList<>();
        if (roleCodes != null) {
            roleCodes.forEach(code -> all.add(new SimpleGrantedAuthority("ROLE_" + code)));
        }
        if (permissions != null) {
            permissions.forEach(perm -> all.add(new SimpleGrantedAuthority(perm)));
        }
        this.authorities = all;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities != null ? authorities : Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
