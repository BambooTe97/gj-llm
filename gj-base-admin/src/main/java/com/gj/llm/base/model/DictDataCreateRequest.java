package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典数据创建请求。
 *
 * @author gj-llm
 */
@Data
public class DictDataCreateRequest {

    /** 所属字典类型编码 */
    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 100, message = "字典类型编码不能超过 100 字符")
    private String dictType;

    /** 显示标签 */
    @NotBlank(message = "标签不能为空")
    @Size(max = 100, message = "标签不能超过 100 字符")
    private String label;

    /** 键值（同类型下唯一） */
    @NotBlank(message = "键值不能为空")
    @Size(max = 100, message = "键值不能超过 100 字符")
    private String dictValue;

    /** 排序（升序） */
    private Integer sort;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过 500 字符")
    private String remark;
}
