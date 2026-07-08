package com.gj.llm.base.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.model.UserCreateRequest;
import com.gj.llm.base.model.UserUpdateRequest;

import java.util.List;

public interface UserService extends IService<UserEntity> {

    List<UserEntity> listAll();

    UserEntity getById(Long id);

    UserEntity create(UserCreateRequest request);

    UserEntity update(Long id, UserUpdateRequest request);

    void delete(Long id);
}
