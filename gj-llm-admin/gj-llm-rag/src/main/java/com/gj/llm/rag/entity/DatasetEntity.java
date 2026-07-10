package com.gj.llm.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库配置实体 —— 映射 {@code dataset} 表。
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dataset")
public class DatasetEntity {

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 知识库名称 */
    private String name;

    /** 描述信息 */
    private String description;

    /** Embedding 模型标识（如 BGE-Large-ZH） */
    private String embeddingModel;

    /** 向量库类型（如 Milvus、PostgreSQL） */
    private String vectorStoreType;

    /** 向量库中的集合名称 */
    private String collectionName;

    /** 切片大小（字符数），默认 800 */
    @Builder.Default
    private Integer chunkSize = 800;

    /** 切片重叠（字符数），默认 100 */
    @Builder.Default
    private Integer chunkOverlap = 100;

    /** 状态：READY=就绪, INDEXING=索引中, ERROR=异常 */
    @Builder.Default
    private String status = "READY";

    /** 文档数量 */
    @Builder.Default
    private Integer docCount = 0;

    /** 向量数量（切片总数） */
    @Builder.Default
    private Integer segmentCount = 0;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
