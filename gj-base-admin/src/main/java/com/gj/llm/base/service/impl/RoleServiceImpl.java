package com.gj.llm.base.service.impl;

import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.mapper.RoleMapper;
import com.gj.llm.base.model.RoleCreateRequest;
import com.gj.llm.base.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理服务实现 —— 基于 MyBatis-Plus，提供角色的增删查操作。
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;

    @Override
    public List<RoleEntity> listAll() {
        return roleMapper.selectList(null);
    }

    @Override
    @Transactional
    public RoleEntity create(RoleCreateRequest request) {
        // 编码唯一性检查
        if (roleMapper.countByCode(request.getCode()) > 0) {
            throw new RuntimeException("角色编码已存在: " + request.getCode());
        }

        RoleEntity role = RoleEntity.builder()
                .name(request.getName())
                .code(request.getCode())
                .description(request.getDescription())
                .build();

        roleMapper.insert(role);
        log.info("创建角色成功: {}", role.getCode());
        return role;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (roleMapper.selectById(id) == null) {
            throw new RuntimeException("角色不存在: id=" + id);
        }
        roleMapper.deleteById(id);
        log.info("删除角色成功: id={}", id);
    }
}
