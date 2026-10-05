package com.gj.llm.rag.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增授权请求体。
 */
@Data
public class AclGrantRequest {

    /** 主体类型：user | role */
    @NotBlank(message = "principalType 不能为空")
    private String principalType;

    /** 主体 ID */
    @NotNull(message = "principalId 不能为空")
    private Long principalId;
}
