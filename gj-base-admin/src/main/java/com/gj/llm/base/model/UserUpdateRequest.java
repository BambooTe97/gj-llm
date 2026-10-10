package com.gj.llm.base.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * 更新用户请求 DTO。
 *
 * @author gj-llm
 */
@Data
public class UserUpdateRequest {

    /** 新昵称 */
    @Size(max = 50, message = "昵称最长 50 个字符")
    private String nickname;

    /** 新邮箱 */
    @Size(max = 100, message = "邮箱最长 100 个字符")
    private String email;

    /** 账户状态：1=启用，0=禁用 */
    @Min(value = 0, message = "status 取值 0-1")
    @Max(value = 1, message = "status 取值 0-1")
    private Integer status;

    /** 所属部门 ID（null=不修改） */
    private Long deptId;

    /** 新角色 ID 集合（全量替换） */
    private Set<Long> roleIds;
}
