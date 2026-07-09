package com.gj.llm.agent.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DatasetCreateRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称最长 100 个字符")
    private String name;

    @Size(max = 500, message = "描述最长 500 个字符")
    private String description;

    @NotBlank(message = "Embedding 模型不能为空")
    @Size(max = 100, message = "模型名称最长 100 个字符")
    private String embeddingModel;

    @NotBlank(message = "向量库类型不能为空")
    @Size(max = 50, message = "向量库类型最长 50 个字符")
    private String vectorStoreType;

    @NotBlank(message = "集合名称不能为空")
    @Size(max = 100, message = "集合名称最长 100 个字符")
    private String collectionName;

    @Min(value = 100, message = "切片大小最小 100")
    @Max(value = 8000, message = "切片大小最大 8000")
    private Integer chunkSize;

    @Min(value = 0, message = "重叠大小最小 0")
    @Max(value = 1000, message = "重叠大小最大 1000")
    private Integer chunkOverlap;
}
