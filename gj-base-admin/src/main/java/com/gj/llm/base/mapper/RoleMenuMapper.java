package com.gj.llm.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gj.llm.base.entity.RoleMenuEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-菜单关联 Mapper -- 管理 {@code sys_role_menu} 中间表。
 *
 * @author gj-llm
 */
public interface RoleMenuMapper extends BaseMapper<RoleMenuEntity> {

    /**
     * 为角色批量分配菜单。
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 ID 列表
     * @return 插入行数
     */
    @Insert("<script>" +
            "INSERT INTO sys_role_menu (role_id, menu_id) VALUES " +
            "<foreach collection='menuIds' item='menuId' separator=','>" +
            "(#{roleId}, #{menuId})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);

    /**
     * 删除角色的所有菜单关联。
     *
     * @param roleId 角色 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色已分配的菜单 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表
     */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);
}
