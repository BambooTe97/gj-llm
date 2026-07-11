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
 * 知识库-文件关联实体 —— 映射 {@code dataset_file} 中间表。
 *
 * <p>关联 {@code dataset} 和 {@code file_record}，
 * 存放知识库特有的处理元数据（状态、错误信息、切片数）。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dataset_file")
public class DatasetFileEntity {

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的知识库 ID */
    private Long datasetId;

    /** 关联的文件记录 ID（file_record.id） */
    private Long fileId;

    /** 处理状态：PENDING=排队中, PROCESSING=向量化中, COMPLETED=完成, FAILED=失败 */
    @Builder.Default
    private String status = "PENDING";

    /** 失败原因 */
    private String errorMessage;

    /** 生成的切片数量 */
    @Builder.Default
    private Integer segmentCount = 0;

    /** 向量化进度百分比 0-100 */
    @Builder.Default
    private Integer progressPercent = 0;

    /** 当前处理阶段描述：读取文件/文本切分/向量嵌入/写入向量库 */
    private String currentStep;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
