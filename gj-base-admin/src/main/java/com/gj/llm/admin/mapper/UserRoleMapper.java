package com.gj.llm.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gj.llm.admin.entity.UserRoleEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户-角色关联 Mapper —— 管理 {@code sys_user_role} 中间表。
 *
 * @author gj-llm
 */
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {

    /**
     * 为用户批量分配角色。
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 列表
     * @return 插入行数
     */
    @Insert("<script>" +
            "INSERT INTO sys_user_role (user_id, role_id) VALUES " +
            "<foreach collection='roleIds' item='roleId' separator=','>" +
            "(#{userId}, #{roleId})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    /**
     * 删除用户的所有角色关联。
     *
     * @param userId 用户 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
