package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 参数配置更新请求。
 *
 * <p>{@code configKey} 创建后不可修改（业务侧按 key 引用，改名会静默破坏消费方）。</p>
 *
 * @author gj-llm
 */
@Data
public class SysConfigUpdateRequest {

    /** 参数名称 */
    @NotBlank(message = "参数名称不能为空")
    @Size(max = 100, message = "参数名称不能超过 100 字符")
    private String name;

    /** 参数键值 */
    @Size(max = 500, message = "参数键值不能超过 500 字符")
    private String configValue;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过 500 字符")
    private String remark;
}
