package com.gj.llm.base.service.impl;

import com.gj.llm.base.entity.MenuEntity;
import com.gj.llm.base.mapper.MenuApiMapper;
import com.gj.llm.base.mapper.MenuMapper;
import com.gj.llm.base.model.MenuUpdateRequest;
import com.gj.llm.base.service.RoleService;
import com.gj.llm.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MenuServiceImpl} 单测 -- 覆盖换父防环（菜单无 ancestors 列，沿 parent 链上溯判环）
 * 与按钮类型不可作父级的守卫逻辑。
 *
 * @author gj-llm
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceImplTest {

    @Mock private MenuMapper menuMapper;
    @Mock private MenuApiMapper menuApiMapper;
    @Mock private RoleService roleService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private MenuServiceImpl menuService;

    @BeforeEach
    void setUp() {
        menuService = new MenuServiceImpl(menuApiMapper, roleService, eventPublisher);
        ReflectionTestUtils.setField(menuService, "baseMapper", menuMapper);
    }

    /** 链形菜单：1(根) ← 2 ← 3；4 为按钮 */
    private List<MenuEntity> chainMenus() {
        return List.of(
                MenuEntity.builder().id(1L).parentId(0L).type("M").name("m1").build(),
                MenuEntity.builder().id(2L).parentId(1L).type("M").name("m2").build(),
                MenuEntity.builder().id(3L).parentId(2L).type("M").name("m3").build(),
                MenuEntity.builder().id(4L).parentId(1L).type("B").name("btn").build());
    }

    @Test
    void updateRejectsMovingMenuUnderOwnDescendant() {
        when(menuMapper.selectById(1L))
                .thenReturn(MenuEntity.builder().id(1L).parentId(0L).type("M").name("m1").build());
        when(menuMapper.selectList(any())).thenReturn(chainMenus());

        MenuUpdateRequest request = new MenuUpdateRequest();
        request.setParentId(3L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.update(1L, request));

        assertEquals("menu.cyclicMove", ex.getMessage());
        verify(menuMapper, never()).updateById(any(MenuEntity.class));
    }

    @Test
    void updateRejectsButtonAsParent() {
        when(menuMapper.selectById(2L))
                .thenReturn(MenuEntity.builder().id(2L).parentId(1L).type("M").name("m2").build());
        when(menuMapper.selectList(any())).thenReturn(chainMenus());

        MenuUpdateRequest request = new MenuUpdateRequest();
        request.setParentId(4L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.update(2L, request));

        assertEquals("menu.buttonAsParent", ex.getMessage());
        verify(menuMapper, never()).updateById(any(MenuEntity.class));
    }

    @Test
    void updateAllowsLegalReparent() {
        when(menuMapper.selectById(3L))
                .thenReturn(MenuEntity.builder().id(3L).parentId(2L).type("M").name("m3").build());
        when(menuMapper.selectList(any())).thenReturn(chainMenus());
        when(menuMapper.updateById(any(MenuEntity.class))).thenReturn(1);

        MenuUpdateRequest request = new MenuUpdateRequest();
        request.setParentId(1L);

        MenuEntity updated = menuService.update(3L, request);

        assertEquals(1L, updated.getParentId());
        verify(menuMapper).updateById(any(MenuEntity.class));
    }

    @Test
    void updateRejectsMissingParent() {
        when(menuMapper.selectById(3L))
                .thenReturn(MenuEntity.builder().id(3L).parentId(2L).type("M").name("m3").build());
        when(menuMapper.selectList(any())).thenReturn(chainMenus());

        MenuUpdateRequest request = new MenuUpdateRequest();
        request.setParentId(999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.update(3L, request));

        assertEquals("menu.parentNotFound", ex.getMessage());
        verify(menuMapper, never()).updateById(any(MenuEntity.class));
    }
}
