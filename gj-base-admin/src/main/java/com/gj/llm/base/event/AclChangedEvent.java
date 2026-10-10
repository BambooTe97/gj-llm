package com.gj.llm.base.event;

/**
 * 资源授权（ACL）变更事件 -- 授权、回收、按主体/资源级联清理时发布，
 * 由 {@code GrantServiceImpl} 在事务提交后失效 {@code auth:grants:*} 授权缓存。
 *
 * <p>事务内直接删缓存存在"已删缓存、未提交"间隙被并发读回填旧值的竞态，
 * 与 {@code UserChangedEvent}/{@code RoleChangedEvent} 同走 AFTER_COMMIT 口径。</p>
 *
 * @author gj-llm
 */
public record AclChangedEvent() {
}
