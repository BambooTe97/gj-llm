package com.gj.llm.base.event;

/**
 * 字典变更事件 -- 字典数据增删改、类型改名级联/删除时发布，
 * 触发字典读缓存失效（事务提交后生效）。
 *
 * @param dictType 变更的字典类型编码；{@code null} 表示全量失效（类型改名/删除）
 * @author gj-llm
 */
public record DictChangedEvent(String dictType) {
}
