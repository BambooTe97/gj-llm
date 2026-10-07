package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典类型创建请求。
 *
 * @author gj-llm
 */
@Data
public class DictTypeCreateRequest {

    /** 字典名称 */
    @NotBlank(message = "字典名称不能为空")
    @Size(max = 100, message = "字典名称不能超过 100 字符")
    private String name;

    /** 字典类型编码（全局唯一，如 sys_status） */
    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 100, message = "字典类型编码不能超过 100 字符")
    private String type;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过 500 字符")
    private String remark;
}
