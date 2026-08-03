package com.gj.llm.base.model;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新菜单请求 DTO（字段可选，仅更新非空字段）。
 *
 * @author gj-llm
 */
@Data
public class MenuUpdateRequest {

    /** 父菜单 ID，0=顶层 */
    private Long parentId;

    /** 菜单名称 */
    @Size(max = 50, message = "菜单名称最长 50 个字符")
    private String name;

    /** 菜单类型：M=目录, C=菜单, B=按钮 */
    @Pattern(regexp = "^[MCB]$", message = "菜单类型只能为 M/C/B")
    private String type;

    /** 路由路径 */
    @Size(max = 200, message = "路由路径最长 200 个字符")
    private String path;

    /** 前端组件路径 */
    @Size(max = 200, message = "组件路径最长 200 个字符")
    private String component;

    /** 权限标识 */
    @Size(max = 100, message = "权限标识最长 100 个字符")
    private String perms;

    /** 图标名称 */
    @Size(max = 100, message = "图标名称最长 100 个字符")
    private String icon;

    /** 排序 */
    private Integer sort;

    /** 是否显示：1=显示, 0=隐藏 */
    private Integer visible;

    /** 状态：1=启用, 0=禁用 */
    private Integer status;
}
