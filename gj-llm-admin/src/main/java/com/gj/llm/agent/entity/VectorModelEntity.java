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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("vector_model")
public class VectorModelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类型编码（唯一，如 medical、story） */
    private String typeCode;

    /** 类型名称（如 医疗知识库） */
    private String typeName;

    /** Milvus 集合名称（如 collection_medical） */
    private String collectionName;

    /** 描述信息 */
    private String description;

    /** 状态：1=启用，0=禁用 */
    @Builder.Default
    private Integer status = 1;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
