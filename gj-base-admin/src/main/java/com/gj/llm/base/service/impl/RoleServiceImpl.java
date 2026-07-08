package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.mapper.RoleMapper;
import com.gj.llm.base.model.RoleCreateRequest;
import com.gj.llm.base.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, RoleEntity> implements RoleService {

    @Override
    public List<RoleEntity> listAll() {
        return list();
    }

    @Override
    @Transactional
    public RoleEntity create(RoleCreateRequest request) {
        long count = count(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getCode, request.getCode()));
        if (count > 0) {
            throw new RuntimeException("角色编码已存在: " + request.getCode());
        }

        RoleEntity role = RoleEntity.builder()
                .name(request.getName())
                .code(request.getCode())
                .description(request.getDescription())
                .build();

        save(role);
        log.info("创建角色成功: {}", role.getCode());
        return role;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new RuntimeException("角色不存在: id=" + id);
        }
        removeById(id);
        log.info("删除角色成功: id={}", id);
    }
}
