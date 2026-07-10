package com.gj.llm.rag.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TestSearchRequest {

    @NotBlank(message = "查询内容不能为空")
    private String query;

    @Min(value = 1, message = "topK 最小为 1")
    @Max(value = 20, message = "topK 最大为 20")
    private Integer topK = 3;
}
