package com.gj.llm.base.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

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

    /**
     * 数据权限域（五档，{@link com.gj.llm.base.entity.RoleEntity} 档位常量）：
     * 1=全部 2=自定义部门 3=本部门 4=本部门及以下 5=仅本人；null 按 1 处理
     */
    @Min(value = 1, message = "数据权限域取值 1-5")
    @Max(value = 5, message = "数据权限域取值 1-5")
    private Integer dataScope;

    /** 自定义部门 ID 集合（仅 dataScope=2 生效） */
    private List<Long> deptIds;
}
