package com.gj.llm.base.model;

import com.gj.llm.security.model.SecurityUser;

import java.util.List;

/**
 * 安全用户缓存数据 -- {@link SecurityUser} 的可序列化缓存载体。
 *
 * <p>{@link SecurityUser} 含 {@code List<GrantedAuthority>}（Spring Security 类型），
 * JSON 反序列化会丢失其具体类型。故缓存此纯字段 DTO：将角色编码（{@code roleCodes}）与
 * 细粒度权限标识（{@code permissions}）分离存储，读取后通过 {@link #toSecurityUser()} 重建 SecurityUser。</p>
 *
 * @author gj-llm
 */
public record SecurityUserCacheData(
        Long userId,
        String username,
        String password,
        String nickname,
        String avatar,
        boolean enabled,
        List<String> roleCodes,
        List<String> permissions
) {

    /**
     * 重建安全用户对象。
     */
    public SecurityUser toSecurityUser() {
        return new SecurityUser(userId, username, password, nickname, avatar, enabled, roleCodes, permissions);
    }
}
