package com.gj.llm.base.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门更新请求。
 *
 * <p>{@code parentId} 允许调整（换父校验：新父存在且不得移入自身子孙，重算祖级链路）。</p>
 *
 * @author gj-llm
 */
@Data
public class DeptUpdateRequest {

    /** 父部门 ID，0=顶级 */
    private Long parentId;

    /** 部门名称 */
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 50, message = "部门名称不能超过 50 字符")
    private String name;

    /** 排序（升序） */
    private Integer sort;

    /** 负责人 */
    @Size(max = 50, message = "负责人不能超过 50 字符")
    private String leader;

    /** 联系电话 */
    @Size(max = 20, message = "联系电话不能超过 20 字符")
    private String phone;

    /** 邮箱 */
    @Size(max = 100, message = "邮箱不能超过 100 字符")
    private String email;

    /** 状态：1=启用，0=停用 */
    @Min(value = 0, message = "status 取值 0-1")
    @Max(value = 1, message = "status 取值 0-1")
    private Integer status;
}
