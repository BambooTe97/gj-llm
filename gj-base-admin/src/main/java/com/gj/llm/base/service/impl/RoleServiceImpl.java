package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.entity.RoleMenuEntity;
import com.gj.llm.base.event.RoleChangedEvent;
import com.gj.llm.base.mapper.RoleMapper;
import com.gj.llm.base.mapper.RoleMenuMapper;
import com.gj.llm.base.model.RoleCreateRequest;
import com.gj.llm.base.model.RoleUpdateRequest;
import com.gj.llm.base.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 角色服务实现 -- 通过 {@link RoleMapper}、{@link RoleMenuMapper} 管理角色与角色-菜单关联。
 *
 * <p>角色变更（更新/删除/分配菜单）时发布 {@link RoleChangedEvent}，由安全用户服务在事务提交后
 * 失效全部用户缓存（用户权限可能随角色-菜单关联变化）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, RoleEntity> implements RoleService {

    private final RoleMenuMapper roleMenuMapper;
    private final ApplicationEventPublisher eventPublisher;

    public RoleServiceImpl(RoleMenuMapper roleMenuMapper, ApplicationEventPublisher eventPublisher) {
        this.roleMenuMapper = roleMenuMapper;
        this.eventPublisher = eventPublisher;
    }

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
    public RoleEntity update(Long id, RoleUpdateRequest request) {
        RoleEntity role = getById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在: id=" + id);
        }
        if (request.getName() != null) {
            role.setName(request.getName());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        updateById(role);
        log.info("更新角色成功: {}", role.getCode());
        // 角色变更可能影响用户展示信息，失效全部用户缓存
        eventPublisher.publishEvent(new RoleChangedEvent());
        return role;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new RuntimeException("角色不存在: id=" + id);
        }
        roleMenuMapper.deleteByRoleId(id);
        removeById(id);
        log.info("删除角色成功: id={}", id);
        // 角色删除影响关联用户权限，失效全部用户缓存
        eventPublisher.publishEvent(new RoleChangedEvent());
    }

    @Override
    @Transactional
    public void assignMenus(Long roleId, Set<Long> menuIds) {
        if (getById(roleId) == null) {
            throw new RuntimeException("角色不存在: id=" + roleId);
        }
        roleMenuMapper.deleteByRoleId(roleId);
        if (menuIds != null && !menuIds.isEmpty()) {
            roleMenuMapper.insertBatch(roleId, List.copyOf(menuIds));
        }
        log.info("角色分配菜单成功: roleId={}, menuCount={}", roleId, menuIds == null ? 0 : menuIds.size());
        // 角色-菜单关联变化直接影响用户权限标识，失效全部用户缓存
        eventPublisher.publishEvent(new RoleChangedEvent());
    }

    @Override
    public RoleEntity getByCode(String code) {
        return getOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getCode, code)
                .last("LIMIT 1"));
    }

    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        return roleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    @Override
    @Transactional
    public void removeMenuFromAllRoles(Long menuId) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuEntity>().eq(RoleMenuEntity::getMenuId, menuId));
        // 菜单从角色移除影响用户权限，失效全部用户缓存
        eventPublisher.publishEvent(new RoleChangedEvent());
    }
}
