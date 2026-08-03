package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.MenuApiEntity;
import com.gj.llm.base.entity.MenuEntity;
import com.gj.llm.base.init.ApiPermissionChangedEvent;
import com.gj.llm.base.mapper.MenuApiMapper;
import com.gj.llm.base.mapper.MenuMapper;
import com.gj.llm.base.model.MenuCreateRequest;
import com.gj.llm.base.model.MenuUpdateRequest;
import com.gj.llm.base.service.MenuService;
import com.gj.llm.base.service.RoleService;
import com.gj.llm.common.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务实现 -- 通过 {@link MenuMapper}、{@link MenuApiMapper} 管理菜单与菜单-接口关联；
 * 删除菜单时通过 {@link RoleService} 清理角色-菜单关联；权限变更通过事件通知缓存刷新。
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class MenuServiceImpl extends ServiceImpl<MenuMapper, MenuEntity> implements MenuService {

    private final MenuApiMapper menuApiMapper;
    private final RoleService roleService;
    private final ApplicationEventPublisher eventPublisher;

    public MenuServiceImpl(MenuApiMapper menuApiMapper, RoleService roleService, ApplicationEventPublisher eventPublisher) {
        this.menuApiMapper = menuApiMapper;
        this.roleService = roleService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<MenuEntity> getMenuTree() {
        return buildTree(list());
    }

    @Override
    public List<MenuEntity> getCurrentUserMenuTree() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return List.of();
        }
        List<MenuEntity> menus = baseMapper.selectMenusByUserId(userId);
        // 前端动态路由/导航仅需目录(M)与菜单(C)，且 visible=1（按钮权限已在 permissions 中返回）
        List<MenuEntity> filtered = menus.stream()
                .filter(m -> !"B".equals(m.getType()))
                .filter(m -> m.getVisible() != null && m.getVisible() == 1)
                .collect(Collectors.toList());
        return buildTree(filtered);
    }

    @Override
    @Transactional
    public MenuEntity create(MenuCreateRequest request) {
        MenuEntity menu = MenuEntity.builder()
                .parentId(request.getParentId())
                .name(request.getName())
                .type(request.getType())
                .path(request.getPath())
                .component(request.getComponent())
                .perms(request.getPerms())
                .icon(request.getIcon())
                .sort(request.getSort() != null ? request.getSort() : 0)
                .visible(request.getVisible() != null ? request.getVisible() : 1)
                .status(request.getStatus() != null ? request.getStatus() : 1)
                .build();
        save(menu);
        log.info("创建菜单成功: {}, id={}", menu.getName(), menu.getId());
        return menu;
    }

    @Override
    @Transactional
    public MenuEntity update(Long id, MenuUpdateRequest request) {
        MenuEntity menu = getById(id);
        if (menu == null) {
            throw new RuntimeException("菜单不存在: id=" + id);
        }
        if (request.getParentId() != null) menu.setParentId(request.getParentId());
        if (request.getName() != null) menu.setName(request.getName());
        if (request.getType() != null) menu.setType(request.getType());
        if (request.getPath() != null) menu.setPath(request.getPath());
        if (request.getComponent() != null) menu.setComponent(request.getComponent());
        if (request.getPerms() != null) menu.setPerms(request.getPerms());
        if (request.getIcon() != null) menu.setIcon(request.getIcon());
        if (request.getSort() != null) menu.setSort(request.getSort());
        if (request.getVisible() != null) menu.setVisible(request.getVisible());
        if (request.getStatus() != null) menu.setStatus(request.getStatus());
        updateById(menu);
        log.info("更新菜单成功: {}", menu.getName());
        // 权限点可能变更，通知缓存刷新
        eventPublisher.publishEvent(new ApiPermissionChangedEvent());
        return menu;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new RuntimeException("菜单不存在: id=" + id);
        }
        long childCount = count(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getParentId, id));
        if (childCount > 0) {
            throw new RuntimeException("存在子菜单，请先删除子节点");
        }
        // 通过 RoleService 清理角色-菜单关联
        roleService.removeMenuFromAllRoles(id);
        // 清理菜单-接口关联（本服务范畴）
        menuApiMapper.deleteByMenuId(id);
        removeById(id);
        eventPublisher.publishEvent(new ApiPermissionChangedEvent());
        log.info("删除菜单成功: id={}", id);
    }

    @Override
    public List<Long> getMenuApiIds(Long menuId) {
        return menuApiMapper.selectList(new LambdaQueryWrapper<MenuApiEntity>()
                        .eq(MenuApiEntity::getMenuId, menuId))
                .stream().map(MenuApiEntity::getApiId).toList();
    }

    @Override
    @Transactional
    public void assignApis(Long menuId, Set<Long> apiIds) {
        if (getById(menuId) == null) {
            throw new RuntimeException("菜单不存在: id=" + menuId);
        }
        menuApiMapper.deleteByMenuId(menuId);
        if (apiIds != null && !apiIds.isEmpty()) {
            apiIds.forEach(apiId -> menuApiMapper.insert(new MenuApiEntity(menuId, apiId)));
        }
        eventPublisher.publishEvent(new ApiPermissionChangedEvent());
        log.info("菜单分配接口成功: menuId={}, apiCount={}", menuId, apiIds == null ? 0 : apiIds.size());
    }

    @Override
    public MenuEntity getByPerms(String perms) {
        return getOne(new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getPerms, perms)
                .last("LIMIT 1"));
    }

    @Override
    public List<String> getPermsByUserId(Long userId) {
        return baseMapper.selectPermsByUserId(userId);
    }

    @Override
    public List<MenuApiEntity> listAllMenuApiLinks() {
        return menuApiMapper.selectList(null);
    }

    @Override
    public List<MenuEntity> listMenusWithPerms() {
        return list(new LambdaQueryWrapper<MenuEntity>()
                .isNotNull(MenuEntity::getPerms)
                .ne(MenuEntity::getPerms, ""));
    }

    @Override
    @Transactional
    public void addApiLinks(List<MenuApiEntity> links) {
        if (links != null && !links.isEmpty()) {
            menuApiMapper.insertBatch(links);
        }
    }

    /**
     * 将平铺菜单列表构建为树形结构（按 sort 升序）。
     */
    private List<MenuEntity> buildTree(List<MenuEntity> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        Map<Long, List<MenuEntity>> byParent = menus.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));
        menus.forEach(m -> {
            List<MenuEntity> children = byParent.getOrDefault(m.getId(), new ArrayList<>());
            children.sort(Comparator.comparingInt(c -> c.getSort() == null ? 0 : c.getSort()));
            m.setChildren(children);
        });
        return menus.stream()
                .filter(m -> m.getParentId() == null || m.getParentId() == 0L)
                .sorted(Comparator.comparingInt(m -> m.getSort() == null ? 0 : m.getSort()))
                .collect(Collectors.toList());
    }
}
