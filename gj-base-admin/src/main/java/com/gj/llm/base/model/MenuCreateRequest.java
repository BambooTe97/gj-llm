package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建菜单请求 DTO。
 *
 * @author gj-llm
 */
@Data
public class MenuCreateRequest {

    /** 父菜单 ID，0=顶层 */
    @NotNull(message = "父菜单ID不能为空")
    private Long parentId;

    /** 菜单名称 */
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称最长 50 个字符")
    private String name;

    /** 菜单类型：M=目录, C=菜单, B=按钮 */
    @NotBlank(message = "菜单类型不能为空")
    @Pattern(regexp = "^[MCB]$", message = "菜单类型只能为 M/C/B")
    private String type;

    /** 路由路径（按钮可为空） */
    @Size(max = 200, message = "路由路径最长 200 个字符")
    private String path;

    /** 前端组件路径（相对 views，目录/按钮可空） */
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
