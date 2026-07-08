package com.gj.llm.base.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.model.RoleCreateRequest;

import java.util.List;

public interface RoleService extends IService<RoleEntity> {

    List<RoleEntity> listAll();

    RoleEntity create(RoleCreateRequest request);

    void delete(Long id);
}
