package com.gj.llm.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gj.llm.base.entity.RoleEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色 Mapper —— 基于 MyBatis-Plus 的 {@link BaseMapper}，自带 CRUD 方法。
 *
 * @author gj-llm
 */
public interface RoleMapper extends BaseMapper<RoleEntity> {

    /**
     * 根据角色编码查询角色。
     *
     * @param code 角色编码，如 {@code ADMIN}
     * @return 角色实体，不存在则 null
     */
    RoleEntity selectByCode(@Param("code") String code);

    /**
     * 统计指定编码的记录数（用于唯一性校验）。
     *
     * @param code 角色编码
     * @return 记录数
     */
    int countByCode(@Param("code") String code);

    /**
     * 根据用户 ID 查询该用户拥有的所有角色。
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    List<RoleEntity> selectByUserId(@Param("userId") Long userId);
}
