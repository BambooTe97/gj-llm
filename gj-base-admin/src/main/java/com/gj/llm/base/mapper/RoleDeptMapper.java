package com.gj.llm.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gj.llm.base.entity.RoleDeptEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-部门关联 Mapper -- 管理 {@code sys_role_dept} 中间表（数据权限自定义部门档）。
 *
 * @author gj-llm
 */
public interface RoleDeptMapper extends BaseMapper<RoleDeptEntity> {

    /**
     * 为角色批量分配可见部门。
     *
     * @param roleId  角色 ID
     * @param deptIds 部门 ID 列表
     * @return 插入行数
     */
    @Insert("<script>" +
            "INSERT INTO sys_role_dept (role_id, dept_id) VALUES " +
            "<foreach collection='deptIds' item='deptId' separator=','>" +
            "(#{roleId}, #{deptId})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("roleId") Long roleId, @Param("deptIds") List<Long> deptIds);

    /**
     * 删除角色的所有部门关联。
     *
     * @param roleId 角色 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM sys_role_dept WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色已分配的部门 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 部门 ID 列表
     */
    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId}")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);
}
