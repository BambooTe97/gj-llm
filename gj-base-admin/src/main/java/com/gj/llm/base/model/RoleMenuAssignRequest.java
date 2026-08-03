package com.gj.llm.base.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

/**
 * 角色分配菜单请求 DTO。
 *
 * <p>传入的菜单 ID 集合为全量替换：Service 先清除角色原有菜单关联，再批量插入新关联。</p>
 *
 * @author gj-llm
 */
@Data
public class RoleMenuAssignRequest {

    /** 菜单 ID 集合（全量替换） */
    @NotNull(message = "菜单ID集合不能为空")
    private Set<Long> menuIds;
}
