package com.gj.llm.agent.entity;

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
 * 切片元数据实体 —— 映射 {@code document_segment} 表。
 *
 * <p>用于调试追踪和删除时定位向量数据库中的向量数据。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_segment")
public class DocumentSegmentEntity {

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的 dataset_file ID */
    private Long datasetFileId;

    /** 对应向量数据库中的 ID */
    private String segmentId;

    /** 文本内容（可选，用于调试） */
    private String content;

    /** JSON 格式存储额外元数据 */
    private String metaData;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
