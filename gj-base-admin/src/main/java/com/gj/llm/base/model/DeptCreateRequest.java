package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门创建请求。
 *
 * @author gj-llm
 */
@Data
public class DeptCreateRequest {

    /** 父部门 ID，0=顶级 */
    @NotNull(message = "父部门不能为空")
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
}
