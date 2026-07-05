package com.gj.llm.security.service;

import com.gj.llm.security.model.SecurityUser;

import java.util.Optional;

/**
 * 安全用户查询服务 —— 安全模块定义的抽象接口。
 *
 * <p>业务模块（如 gj-core-admin）需要实现此接口，提供从数据库或其他持久化层
 * 加载用户信息的能力。安全模块通过该接口完成认证，而不直接耦合数据库。</p>
 *
 * @author gj-llm
 */
public interface SecurityUserService {

    /**
     * 根据用户名查找用户。
     *
     * @param username 用户名（登录凭证）
     * @return 用户信息，若不存在则返回 {@link Optional#empty()}
     */
    Optional<SecurityUser> findByUsername(String username);
}
