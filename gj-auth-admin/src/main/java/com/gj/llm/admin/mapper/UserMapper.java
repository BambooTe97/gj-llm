package com.gj.llm.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gj.llm.admin.entity.UserEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper —— 基于 MyBatis-Plus 的 {@link BaseMapper}，自带 CRUD 方法。
 *
 * <p>由 {@code MyBatisGlobalConfig} 上的 {@code @MapperScan} 统一扫描注册，
 * 无需单独标注 {@code @Mapper}。</p>
 *
 * @author gj-llm
 */
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * 根据用户名查询用户。
     *
     * @param username 用户名
     * @return 用户实体，不存在则 null
     */
    UserEntity selectByUsername(@Param("username") String username);

    /**
     * 统计指定用户名的记录数（用于唯一性校验）。
     *
     * @param username 用户名
     * @return 记录数
     */
    int countByUsername(@Param("username") String username);
}
