package com.gj.llm.base.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

/**
 * 菜单按钮分配接口请求 DTO。
 *
 * <p>传入的接口 ID 集合为全量替换：Service 先清除菜单按钮原有接口关联，再批量插入，
 * 并刷新接口权限缓存。</p>
 *
 * @author gj-llm
 */
@Data
public class MenuApiAssignRequest {

    /** 接口 ID 集合（全量替换） */
    @NotNull(message = "接口ID集合不能为空")
    private Set<Long> apiIds;
}
