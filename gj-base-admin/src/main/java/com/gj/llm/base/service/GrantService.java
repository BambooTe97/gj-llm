package com.gj.llm.base.service;

import java.util.Set;

/**
 * 授权判定服务接口 —— 用户→角色解析（复用本模块 {@link UserService}/{@link RoleService}）、
 * 资源授权集合查询与管理员旁路判定。
 *
 * <p>纯判定，不写库：授权写操作在 {@link ResourceAclService}，写后由其失效缓存。
 * 所有结果带 Redis 缓存（TTL 兜底 + 写后主动失效），避免聊天链路每次请求打库。</p>
 *
 * <p><b>红线</b>：实现不触碰 ThreadLocal（SecurityUtils / SecurityContextHolder），
 * userId 一律由调用方显式传入 —— 可在任意线程池线程安全调用。</p>
 *
 * @author gj-llm
 */
public interface GrantService {

    /**
     * 用户被授权（主体=本人或其角色）的资源 ID 集合，缓存 {@code auth:grants:u{uid}:{type}}。
     */
    Set<Long> grantedResourceIds(Long userId, String resourceType);

    /**
     * 用户对指定资源是否有直接授权（本人或角色主体）。
     */
    boolean hasGrant(Long userId, String resourceType, Long resourceId);

    /**
     * 用户是否持有管理员角色（全库可见可管旁路）。
     */
    boolean isAdmin(Long userId);

    /**
     * 失效全部授权缓存（授权写后调用；角色主体授权影响面不可知，按模式全清）。
     *
     * @return 删除的缓存键数量
     */
    long invalidateAllGrants();
}
