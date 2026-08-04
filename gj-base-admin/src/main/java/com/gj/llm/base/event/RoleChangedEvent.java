package com.gj.llm.base.event;

/**
 * 角色变更事件 -- 角色更新、删除、分配菜单时发布，
 * 触发全部安全用户缓存失效（用户权限可能随角色变化）。
 *
 * @author gj-llm
 */
public record RoleChangedEvent() {
}
