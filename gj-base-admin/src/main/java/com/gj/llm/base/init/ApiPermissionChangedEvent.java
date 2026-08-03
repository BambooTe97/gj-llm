package com.gj.llm.base.init;

/**
 * 接口权限变更事件 -- 菜单/接口关联或菜单权限点变更时发布，
 * 由 {@link ApiPermissionCache} 监听并刷新缓存，避免 Service 与缓存间循环依赖。
 *
 * @author gj-llm
 */
public class ApiPermissionChangedEvent {
}
