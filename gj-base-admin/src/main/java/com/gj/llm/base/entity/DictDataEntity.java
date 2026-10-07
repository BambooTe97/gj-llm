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
 * 字典数据实体 -- 映射 {@code sys_dict_data} 表。
 *
 * <p>{@code dictType} 冗余存储字典类型编码（非外键），类型改名时由 Service 级联更新；
 * {@code (dictType, dictValue)} 唯一。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class DictDataEntity extends BaseEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属字典类型编码 */
    private String dictType;

    /** 显示标签 */
    private String label;

    /** 键值 */
    private String dictValue;

    /** 排序（升序，越小越靠前） */
    private Integer sort;

    /** 状态：1=启用，0=停用 */
    @Builder.Default
    private Integer status = 1;

    /** 备注 */
    private String remark;
}
