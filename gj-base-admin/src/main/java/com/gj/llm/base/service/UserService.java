package com.gj.llm.base.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.model.UserCreateRequest;
import com.gj.llm.base.model.UserUpdateRequest;

import java.util.List;

/**
 * 用户服务接口 -- 用户增删改查、分页、重置密码，及供认证层调用的查询能力。
 *
 * @author gj-llm
 */
public interface UserService extends IService<UserEntity> {

    /** 用户列表（含角色） */
    List<UserEntity> listAll();

    /** 分页查询用户（含角色，支持用户名/昵称模糊搜索） */
    IPage<UserEntity> page(long pageNum, long size, String keyword);

    /** 用户详情（含角色） */
    UserEntity getById(Long id);

    /** 创建用户 */
    UserEntity create(UserCreateRequest request);

    /** 更新用户 */
    UserEntity update(Long id, UserUpdateRequest request);

    /** 重置用户密码 */
    void resetPassword(Long id, String newPassword);

    /** 删除用户（清理用户-角色关联） */
    void delete(Long id);

    /** 查询用户的角色 ID 列表（供认证层加载） */
    List<Long> getRoleIdsByUserId(Long userId);

    /** 按用户名查询用户（含密码，供认证层使用，不对外暴露密码） */
    UserEntity findByUsername(String username);
}
