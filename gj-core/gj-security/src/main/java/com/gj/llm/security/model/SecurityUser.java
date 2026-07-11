package com.gj.llm.security.model;

import com.gj.llm.common.util.UserIdProvider;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

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

    /** 权限列表 */
    private final List<GrantedAuthority> authorities;

    public SecurityUser(Long userId, String username, String password,
                        String nickname, String avatar,
                        boolean enabled, List<String> roleCodes) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.avatar = avatar;
        this.enabled = enabled;
        // Spring Security 角色前缀默认为 "ROLE_"
        this.authorities = roleCodes.stream()
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                .map(ga -> (GrantedAuthority) ga)
                .toList();
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
