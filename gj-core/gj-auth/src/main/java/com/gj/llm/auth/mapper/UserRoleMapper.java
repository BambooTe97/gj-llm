package com.gj.llm.auth.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户角色/主体信息 Mapper —— 对 gj-base-admin 的 {@code sys_user_role} / {@code sys_user} /
 * {@code sys_role} 表做<b>只读</b>查询（同库直读，避免 auth 模块反向依赖业务模块）。
 *
 * @author gj-llm
 */
public interface UserRoleMapper {

    /** 用户的角色 ID 集合（授权主体匹配用） */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIds(@Param("userId") Long userId);

    /** 用户的角色编码集合（管理员判定用，与 AuthProperties.adminRoles 比对） */
    @Select("SELECT r.code FROM sys_role r INNER JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodes(@Param("userId") Long userId);

    /** 用户登录名（主体展示名兜底） */
    @Select("SELECT username FROM sys_user WHERE id = #{userId}")
    String selectUsername(@Param("userId") Long userId);

    /** 用户昵称（可能为 null） */
    @Select("SELECT nickname FROM sys_user WHERE id = #{userId}")
    String selectNickname(@Param("userId") Long userId);

    /** 角色名称（主体展示名） */
    @Select("SELECT name FROM sys_role WHERE id = #{roleId}")
    String selectRoleName(@Param("roleId") Long roleId);

    /** 用户是否存在（授权前校验） */
    @Select("SELECT COUNT(1) FROM sys_user WHERE id = #{userId}")
    long countUser(@Param("userId") Long userId);

    /** 角色是否存在（授权前校验） */
    @Select("SELECT COUNT(1) FROM sys_role WHERE id = #{roleId}")
    long countRole(@Param("roleId") Long roleId);

    /** 按关键词搜索启用用户（共享面板主体选择器，仅返回 id + 展示名） */
    @Select("SELECT id, CONCAT(nickname, ' (', username, ')') AS name FROM sys_user "
            + "WHERE status = 1 AND (username LIKE CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%')) "
            + "ORDER BY created_at DESC LIMIT 20")
    List<PrincipalOption> searchUsers(@Param("keyword") String keyword);

    /** 全量角色列表（共享面板主体选择器） */
    @Select("SELECT id, name FROM sys_role ORDER BY id LIMIT 50")
    List<PrincipalOption> listRoles();

    /** 主体选择项（id + 展示名），MyBatis 按列别名自动映射 setter */
    @lombok.Data
    class PrincipalOption {

        private Long id;

        private String name;
    }
}
