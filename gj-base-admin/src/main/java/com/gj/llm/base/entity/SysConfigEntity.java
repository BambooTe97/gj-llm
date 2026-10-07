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
 * 参数配置实体 -- 映射 {@code sys_config} 表。
 *
 * <p>运行时可调参数（避免业务参数写死 application.yml），{@code configKey} 全局唯一；
 * {@code builtIn=1} 为内置参数，不允许删除（值可修改）。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfigEntity extends BaseEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 参数名称（展示用） */
    private String name;

    /** 参数键名（唯一，业务侧引用，创建后不可修改） */
    private String configKey;

    /** 参数键值 */
    private String configValue;

    /** 是否内置：1=内置（不可删除），0=自定义 */
    @Builder.Default
    private Integer builtIn = 0;

    /** 备注 */
    private String remark;
}
