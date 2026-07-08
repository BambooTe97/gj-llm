package com.gj.llm.base.service;

import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.model.UserCreateRequest;
import com.gj.llm.base.model.UserUpdateRequest;

import java.util.List;

/**
 * 用户管理服务接口 —— 提供用户 CRUD 操作。
 *
 * @author gj-llm
 */
public interface UserService {

    /**
     * 查询所有用户。
     *
     * @return 用户列表
     */
    List<UserEntity> listAll();

    /**
     * 根据 ID 查询用户。
     *
     * @param id 用户 ID
     * @return 用户实体
     */
    UserEntity getById(Long id);

    /**
     * 创建用户。
     *
     * @param request 创建请求
     * @return 新建的用户实体
     */
    UserEntity create(UserCreateRequest request);

    /**
     * 更新用户。
     *
     * @param id      用户 ID
     * @param request 更新请求
     * @return 更新后的用户实体
     */
    UserEntity update(Long id, UserUpdateRequest request);

    /**
     * 删除用户。
     *
     * @param id 用户 ID
     */
    void delete(Long id);
}
