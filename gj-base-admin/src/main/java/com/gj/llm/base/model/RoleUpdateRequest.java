package com.gj.llm.base.model;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新角色请求 DTO。
 *
 * <p>角色编码（code）为权限判断的唯一标识，不允许修改，故不在此 DTO 中。</p>
 *
 * @author gj-llm
 */
@Data
public class RoleUpdateRequest {

    /** 角色名称（展示用） */
    @Size(max = 50, message = "角色名称最长 50 个字符")
    private String name;

    /** 角色描述 */
    @Size(max = 200, message = "描述最长 200 个字符")
    private String description;
}
