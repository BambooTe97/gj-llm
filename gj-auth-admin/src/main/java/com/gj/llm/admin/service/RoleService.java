package com.gj.llm.admin.service;

import com.gj.llm.admin.entity.RoleEntity;
import com.gj.llm.admin.model.RoleCreateRequest;

import java.util.List;

/**
 * 角色管理服务接口 —— 提供角色 CRUD 操作。
 *
 * @author gj-llm
 */
public interface RoleService {

    /**
     * 查询所有角色。
     *
     * @return 角色列表
     */
    List<RoleEntity> listAll();

    /**
     * 创建角色。
     *
     * @param request 创建请求
     * @return 新建的角色实体
     */
    RoleEntity create(RoleCreateRequest request);

    /**
     * 删除角色。
     *
     * @param id 角色 ID
     */
    void delete(Long id);
}
