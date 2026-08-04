package com.gj.llm.base.event;

/**
 * 用户变更事件 -- 用户更新、重置密码、删除时发布，
 * 触发安全用户缓存按用户名失效（事务提交后生效）。
 *
 * @param username 变更用户的用户名
 * @author gj-llm
 */
public record UserChangedEvent(String username) {
}
