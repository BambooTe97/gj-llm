package com.gj.llm.common.util;

/**
 * 用户 ID 提供者 —— 由安全模块的 {@code SecurityUser} 实现，
 * 使 {@link SecurityUtils#getCurrentUserId()} 无需直接依赖安全模块即可获取用户 ID。
 *
 * @author gj-llm
 */
@FunctionalInterface
public interface UserIdProvider {

    /** 获取当前登录用户的数据库主键 ID */
    Long getUserId();
}
