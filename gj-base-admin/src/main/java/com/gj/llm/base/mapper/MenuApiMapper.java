package com.gj.llm.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gj.llm.base.entity.MenuApiEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单按钮-接口关联 Mapper -- 管理 {@code sys_menu_api} 中间表。
 *
 * @author gj-llm
 */
public interface MenuApiMapper extends BaseMapper<MenuApiEntity> {

    /**
     * 删除菜单按钮的所有接口关联（重新分配前清空）。
     *
     * @param menuId 菜单/按钮 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM sys_menu_api WHERE menu_id = #{menuId}")
    int deleteByMenuId(@Param("menuId") Long menuId);

    /**
     * 批量插入菜单-接口关联。
     *
     * @param list 关联列表
     * @return 插入行数
     */
    @Insert("<script>" +
            "INSERT INTO sys_menu_api (menu_id, api_id) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.menuId}, #{item.apiId})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<MenuApiEntity> list);
}
