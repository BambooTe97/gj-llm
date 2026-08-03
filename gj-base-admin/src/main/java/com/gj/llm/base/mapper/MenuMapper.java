package com.gj.llm.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gj.llm.base.entity.MenuEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单 Mapper -- 管理 {@code sys_menu} 表，并支持按用户/角色查询菜单与权限。
 *
 * <p>简单 CRUD 由 {@link BaseMapper} 提供；按用户查询菜单、权限标识，
 * 按角色查询已分配菜单 ID 等多表关联查询在 {@code MenuMapper.xml} 中以 SQL 实现。</p>
 *
 * @author gj-llm
 */
public interface MenuMapper extends BaseMapper<MenuEntity> {

    /**
     * 查询用户可访问的全部菜单（含目录/菜单/按钮，按角色聚合去重）。
     *
     * @param userId 用户 ID
     * @return 菜单列表（按 sort 升序）
     */
    List<MenuEntity> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的全部权限标识（仅菜单/按钮类型且 perms 非空）。
     *
     * @param userId 用户 ID
     * @return 权限标识列表（去重）
     */
    List<String> selectPermsByUserId(@Param("userId") Long userId);
}
