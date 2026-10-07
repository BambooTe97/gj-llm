package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 参数配置创建请求。
 *
 * @author gj-llm
 */
@Data
public class SysConfigCreateRequest {

    /** 参数名称 */
    @NotBlank(message = "参数名称不能为空")
    @Size(max = 100, message = "参数名称不能超过 100 字符")
    private String name;

    /** 参数键名（全局唯一，创建后不可修改） */
    @NotBlank(message = "参数键名不能为空")
    @Size(max = 100, message = "参数键名不能超过 100 字符")
    private String configKey;

    /** 参数键值 */
    @Size(max = 500, message = "参数键值不能超过 500 字符")
    private String configValue;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过 500 字符")
    private String remark;
}
