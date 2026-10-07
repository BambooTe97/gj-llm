package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典类型更新请求。
 *
 * <p>{@code type} 允许修改（改名校验唯一后级联更新字典数据的 dict_type）。</p>
 *
 * @author gj-llm
 */
@Data
public class DictTypeUpdateRequest {

    /** 字典名称 */
    @NotBlank(message = "字典名称不能为空")
    @Size(max = 100, message = "字典名称不能超过 100 字符")
    private String name;

    /** 字典类型编码（改名校验唯一后级联更新数据） */
    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 100, message = "字典类型编码不能超过 100 字符")
    private String type;

    /** 状态：1=启用，0=停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过 500 字符")
    private String remark;
}
