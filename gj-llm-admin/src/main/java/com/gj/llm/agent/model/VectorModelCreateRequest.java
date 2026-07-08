package com.gj.llm.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VectorModelCreateRequest {

    @NotBlank(message = "类型编码不能为空")
    @Size(max = 50, message = "类型编码最长 50 个字符")
    private String typeCode;

    @NotBlank(message = "类型名称不能为空")
    @Size(max = 100, message = "类型名称最长 100 个字符")
    private String typeName;

    @NotBlank(message = "集合名称不能为空")
    @Size(max = 100, message = "集合名称最长 100 个字符")
    private String collectionName;

    @Size(max = 500, message = "描述最长 500 个字符")
    private String description;
}
