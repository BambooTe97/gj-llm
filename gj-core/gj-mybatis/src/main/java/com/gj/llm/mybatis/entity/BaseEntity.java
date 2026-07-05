package com.gj.llm.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类 —— 提供公共审计字段。
 *
 * <p>子类实体直接继承即可获得自动填充能力，无需在每个实体中重复声明。
 * 自动填充由 {@link com.gj.llm.mybatis.config.MyBatisGlobalConfig#metaObjectHandler()} 统一处理。</p>
 *
 * <p><b>使用前提：</b>数据库表中需存在对应的 {@code create_by}、{@code update_by}、
 * {@code created_at}、{@code updated_at} 列。</p>
 *
 * @author gj-llm
 */
@Data
public abstract class BaseEntity {

    /** 创建者（INSERT 时由 MetaObjectHandler 自动填充当前用户名） */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** 更新者（INSERT / UPDATE 时由 MetaObjectHandler 自动填充当前用户名） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 创建时间（INSERT 时由 MetaObjectHandler 自动填充 {@link LocalDateTime#now()}） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（INSERT / UPDATE 时由 MetaObjectHandler 自动填充 {@link LocalDateTime#now()}） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
