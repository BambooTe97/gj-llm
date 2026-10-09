package com.gj.llm.base.model;

import java.util.Set;

/**
 * 数据权限域 -- 当前登录用户在数据查询中可见的范围（角色数据域五档解析结果）。
 *
 * <p>语义为并集聚合：多角色时取各角色数据域的并集（最宽生效）。</p>
 *
 * <ul>
 *   <li>{@code all=true}：全部数据，不加过滤条件</li>
 *   <li>{@code deptIds}：可见的部门 ID 集合（自定义/本部门/本部门及以下归并于此）</li>
 *   <li>{@code selfUserIds}：仅本人的用户 ID 集合（"仅本人"档归并于此）</li>
 * </ul>
 *
 * <p>三者可并存（如一人同时持有"自定义"与"仅本人"角色），查询侧按
 * {@code dept_id IN deptIds OR id IN selfUserIds} 组合。三者为空且 all=false 即空域
 * （fail-closed：查不到任何数据，不报错）。</p>
 *
 * @param all         是否全部数据（绕过过滤）
 * @param deptIds     可见部门 ID 集合（可为空）
 * @param selfUserIds 仅本人可见的用户 ID 集合（可为空）
 * @author gj-llm
 */
public record DataScope(boolean all, Set<Long> deptIds, Set<Long> selfUserIds) {

    /** 空域常量 -- 未登录/无角色时的 fail-closed 结果（查询结果为空） */
    public static final DataScope EMPTY = new DataScope(false, Set.of(), Set.of());

    /** 全域常量 -- 管理员角色/全员档（不加任何过滤） */
    public static final DataScope ALL = new DataScope(true, Set.of(), Set.of());

    /** 是否空域（all=false 且两个集合均空） */
    public boolean isEmpty() {
        return !all && deptIds.isEmpty() && selfUserIds.isEmpty();
    }
}
