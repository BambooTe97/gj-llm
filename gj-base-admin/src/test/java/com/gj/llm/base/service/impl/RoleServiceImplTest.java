package com.gj.llm.base.service.impl;

import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.event.AclChangedEvent;
import com.gj.llm.base.event.RoleChangedEvent;
import com.gj.llm.base.mapper.MenuMapper;
import com.gj.llm.base.mapper.ResourceAclMapper;
import com.gj.llm.base.mapper.RoleDeptMapper;
import com.gj.llm.base.mapper.RoleMapper;
import com.gj.llm.base.mapper.RoleMenuMapper;
import com.gj.llm.base.mapper.UserRoleMapper;
import com.gj.llm.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RoleServiceImpl} 单测 -- 覆盖内置角色保护、删除级联清理、菜单分配校验。
 *
 * <p>ServiceImpl 的 baseMapper 通过反射注入（不启 Spring 上下文）。</p>
 *
 * @author gj-llm
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock private RoleMapper roleMapper;
    @Mock private RoleMenuMapper roleMenuMapper;
    @Mock private RoleDeptMapper roleDeptMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private MenuMapper menuMapper;
    @Mock private ResourceAclMapper resourceAclMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(roleMenuMapper, roleDeptMapper, userRoleMapper,
                menuMapper, resourceAclMapper, new AuthProperties(), eventPublisher);
        ReflectionTestUtils.setField(roleService, "baseMapper", roleMapper);
    }

    // ==================== 内置角色保护 ====================

    @Test
    void deleteRejectsBuiltInAdminRole() {
        when(roleMapper.selectById(1L)).thenReturn(RoleEntity.builder().id(1L).code("ADMIN").build());

        BusinessException ex = assertThrows(BusinessException.class, () -> roleService.delete(1L));

        assertEquals("role.builtinDeleteDenied", ex.getMessage());
        verify(roleMenuMapper, never()).deleteByRoleId(anyLong());
        verify(userRoleMapper, never()).deleteByRoleId(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deleteRejectsDefaultUserRole() {
        when(roleMapper.selectById(2L)).thenReturn(RoleEntity.builder().id(2L).code("USER").build());

        BusinessException ex = assertThrows(BusinessException.class, () -> roleService.delete(2L));

        assertEquals("role.builtinDeleteDenied", ex.getMessage());
        verify(userRoleMapper, never()).deleteByRoleId(anyLong());
    }

    // ==================== 删除级联清理 ====================

    @Test
    void deleteCascadesUserRoleAndRoleAcl() {
        when(roleMapper.selectById(7L)).thenReturn(RoleEntity.builder().id(7L).code("MANAGER").build());
        when(roleMapper.deleteById(7L)).thenReturn(1);

        roleService.delete(7L);

        InOrder order = inOrder(roleMenuMapper, roleDeptMapper, userRoleMapper, resourceAclMapper, roleMapper);
        order.verify(roleMenuMapper).deleteByRoleId(7L);
        order.verify(roleDeptMapper).deleteByRoleId(7L);
        // 级联清理用户关联（授权判定按 user_role 聚合）
        order.verify(userRoleMapper).deleteByRoleId(7L);
        // 级联清理角色主体的资源授权行
        order.verify(resourceAclMapper).delete(any());
        order.verify(roleMapper).deleteById(7L);
        verify(eventPublisher).publishEvent(any(RoleChangedEvent.class));
        verify(eventPublisher).publishEvent(any(AclChangedEvent.class));
    }

    // ==================== 菜单分配校验 ====================

    @Test
    void assignMenusRejectsNonExistentMenuIds() {
        when(roleMapper.selectById(7L)).thenReturn(RoleEntity.builder().id(7L).code("MANAGER").build());
        // 传入 2 个菜单 ID 但库里只有 1 个
        when(menuMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.assignMenus(7L, Set.of(100L, 200L)));

        assertEquals("menu.idInvalid", ex.getMessage());
        verify(roleMenuMapper, never()).insertBatch(anyLong(), anyList());
    }

    @Test
    void assignMenusWritesDistinctBatch() {
        when(roleMapper.selectById(7L)).thenReturn(RoleEntity.builder().id(7L).code("MANAGER").build());
        when(menuMapper.selectCount(any())).thenReturn(2L);

        // Set.of 不允许重复，用 HashSet 构造带重复元素的入参验证去重
        Set<Long> duplicatedIds = new HashSet<>(List.of(100L, 100L, 200L));

        roleService.assignMenus(7L, duplicatedIds);

        // 去重后批量写入
        verify(roleMenuMapper).insertBatch(7L, List.of(100L, 200L));
        verify(eventPublisher).publishEvent(any(RoleChangedEvent.class));
    }
}
