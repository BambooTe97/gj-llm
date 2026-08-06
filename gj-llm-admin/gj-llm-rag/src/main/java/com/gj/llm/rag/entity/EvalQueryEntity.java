package com.gj.llm.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gj.llm.mybatis.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 检索评测用例实体 -- 映射 {@code dataset_retrieval_eval_query} 表。
 *
 * <p>按知识库持久化"评测 query + 期望命中依据"，供离线评测 Recall@K / MRR。
 * {@link #expectedSnippet} 须为 chunk/父块原文的逐字子串（{@code matches()} 做包含匹配），
 * 由外部强 AI 按模板生成后导入，或从检索测试页"标为期望答案"沉淀。</p>
 *
 * <p>审计字段（createBy/updateBy/createdAt/updatedAt）继承自 {@link BaseEntity}，
 * 由 {@code MetaObjectHandler} 自动填充。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("dataset_retrieval_eval_query")
public class EvalQueryEntity extends BaseEntity {

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的知识库 ID */
    private Long datasetId;

    /** 评测查询（原始，不走改写，直接测索引+reranker 质量） */
    private String query;

    /** 期望命中来源文件名（与 source 元数据做包含匹配，可只填部分） */
    private String expectedSource;

    /** 期望命中文本片段（对子块/父块原文做逐字子串匹配，须原文摘录不可改写） */
    private String expectedSnippet;
}
