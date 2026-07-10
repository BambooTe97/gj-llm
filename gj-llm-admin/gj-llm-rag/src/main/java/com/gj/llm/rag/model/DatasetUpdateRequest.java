package com.gj.llm.rag.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DatasetUpdateRequest {

    @Size(max = 100, message = "知识库名称最长 100 个字符")
    private String name;

    @Size(max = 500, message = "描述最长 500 个字符")
    private String description;

    @Size(max = 100, message = "模型名称最长 100 个字符")
    private String embeddingModel;

    @Min(value = 100, message = "切片大小最小 100")
    @Max(value = 8000, message = "切片大小最大 8000")
    private Integer chunkSize;

    @Min(value = 0, message = "重叠大小最小 0")
    @Max(value = 1000, message = "重叠大小最大 1000")
    private Integer chunkOverlap;
}
