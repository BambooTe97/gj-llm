package com.gj.llm.agent.model;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VectorModelUpdateRequest {

    @Size(max = 100, message = "类型名称最长 100 个字符")
    private String typeName;

    @Size(max = 100, message = "集合名称最长 100 个字符")
    private String collectionName;

    @Size(max = 500, message = "描述最长 500 个字符")
    private String description;

    /** 状态：1=启用，0=禁用，null=不修改 */
    private Integer status;
}
