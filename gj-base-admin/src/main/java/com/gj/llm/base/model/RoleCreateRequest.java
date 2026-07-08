package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建角色请求 DTO。
 *
 * @author gj-llm
 */
@Data
public class RoleCreateRequest {

    /** 角色名称（展示用），例如 "系统管理员" */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称最长 50 个字符")
    private String name;

    /** 角色编码（权限判断用），例如 ADMIN，需唯一 */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码最长 50 个字符")
    private String code;

    /** 角色描述 */
    @Size(max = 200, message = "描述最长 200 个字符")
    private String description;
}
