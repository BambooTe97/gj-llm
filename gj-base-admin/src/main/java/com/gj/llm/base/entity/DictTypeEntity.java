package com.gj.llm.base.entity;

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
 * 字典类型实体 -- 映射 {@code sys_dict_type} 表。
 *
 * <p>{@code type} 为全局唯一编码（如 {@code sys_status}），
 * 字典数据（{@link DictDataEntity}）通过该编码关联。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class DictTypeEntity extends BaseEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 字典名称（展示用） */
    private String name;

    /** 字典类型编码（唯一，业务侧引用） */
    private String type;

    /** 状态：1=启用，0=停用 */
    @Builder.Default
    private Integer status = 1;

    /** 备注 */
    private String remark;
}
