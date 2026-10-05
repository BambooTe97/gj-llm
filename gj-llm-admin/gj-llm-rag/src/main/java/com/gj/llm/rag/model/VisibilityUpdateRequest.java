package com.gj.llm.rag.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 可见性切换请求体。
 */
@Data
public class VisibilityUpdateRequest {

    /** 目标可见性：PUBLIC / RESTRICTED */
    @NotBlank(message = "visibility 不能为空")
    private String visibility;
}
