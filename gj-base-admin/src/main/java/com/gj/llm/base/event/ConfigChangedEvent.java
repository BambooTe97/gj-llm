package com.gj.llm.base.event;

/**
 * 参数变更事件 -- 参数增删改时发布，触发参数读缓存失效（事务提交后生效）。
 *
 * @param configKey 变更的参数键名；当前实现总是非 null（键名创建后不可改，删除按旧键失效）
 * @author gj-llm
 */
public record ConfigChangedEvent(String configKey) {
}
