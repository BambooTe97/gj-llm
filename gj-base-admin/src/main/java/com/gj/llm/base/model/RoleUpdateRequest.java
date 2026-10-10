package com.gj.llm.base.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

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

    /** 数据权限域（五档）：1=全部 2=自定义部门 3=本部门 4=本部门及以下 5=仅本人；null 不变更 */
    @Min(value = 1, message = "数据权限域取值 1-5")
    @Max(value = 5, message = "数据权限域取值 1-5")
    private Integer dataScope;

    /** 自定义部门 ID 集合（仅 dataScope=2 生效；≠2 时清空已有部门关联） */
    private List<Long> deptIds;
}
