package com.gj.llm.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gj.llm.mybatis.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 部门实体 -- 映射 {@code sys_dept} 表，树形结构。
 *
 * <p>通过 {@code parent_id} 自关联（0=顶级）+ {@code ancestors} 祖级链路
 * （如 {@code 0,100}）支持子树过滤；{@code children} 由 Service 层构建树时填充。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDeptEntity extends BaseEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父部门 ID，0=顶级 */
    private Long parentId;

    /** 祖级列表，如 0,100 */
    private String ancestors;

    /** 部门名称 */
    private String name;

    /** 排序（升序，越小越靠前） */
    private Integer sort;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态：1=启用，0=停用 */
    @Builder.Default
    private Integer status = 1;

    /**
     * 子部门集合 -- 不映射数据库字段，由 Service 层构建部门树时填充。
     */
    @TableField(exist = false)
    private List<SysDeptEntity> children;
}
