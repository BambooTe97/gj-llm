package com.gj.llm.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色-部门关联实体 —— 映射 {@code sys_role_dept} 中间表（MyBatis-Plus）。
 *
 * <p>数据权限"自定义部门"档（{@code data_scope=2}）的角色可见部门集合，
 * 复合主键 (role_id, dept_id) 由 DDL 定义。</p>
 *
 * @author gj-llm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role_dept")
public class RoleDeptEntity {

    /** 角色 ID */
    private Long roleId;

    /** 部门 ID */
    private Long deptId;
}
