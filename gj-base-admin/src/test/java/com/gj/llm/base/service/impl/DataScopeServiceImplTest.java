package com.gj.llm.base.service.impl;

import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.mapper.RoleDeptMapper;
import com.gj.llm.base.model.DataScope;
import com.gj.llm.base.service.RoleService;
import com.gj.llm.base.service.SysDeptService;
import com.gj.llm.base.service.UserService;
import com.gj.llm.security.model.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * {@link DataScopeServiceImpl} 单测 -- 覆盖五档数据域解析与聚合规则：
 * 未认证空域（fail-closed）、管理员绕过、多角色取最宽、五档部门/本人并集。
 *
 * @author gj-llm
 */
@ExtendWith(MockitoExtension.class)
class DataScopeServiceImplTest {

    @Mock private RoleService roleService;
    @Mock private SysDeptService sysDeptService;
    @Mock private RoleDeptMapper roleDeptMapper;
    @Mock private UserService userService;

    private AuthProperties authProperties;
    private DataScopeServiceImpl dataScopeService;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        dataScopeService = new DataScopeServiceImpl(roleService, sysDeptService,
                roleDeptMapper, authProperties, userService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 在 SecurityContext 放入指定 userId 的认证主体 */
    private void loginAs(long userId) {
        SecurityUser principal = org.mockito.Mockito.mock(SecurityUser.class);
        when(principal.getUserId()).thenReturn(userId);
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void unauthenticatedResolvesToEmptyScope() {
        // 无 SecurityContext → 空域（fail-closed）
        DataScope scope = dataScopeService.resolve();

        assertFalse(scope.all());
        assertTrue(scope.isEmpty());
    }

    @Test
    void noRolesResolvesToEmptyScope() {
        loginAs(1L);
        when(userService.getRoleIdsByUserId(1L)).thenReturn(List.of());

        DataScope scope = dataScopeService.resolve();

        assertTrue(scope.isEmpty());
    }

    @Test
    void adminRoleResolvesToAllScope() {
        loginAs(1L);
        when(userService.getRoleIdsByUserId(1L)).thenReturn(List.of(10L));
        when(roleService.listByIds(anyCollection()))
                .thenReturn(List.of(RoleEntity.builder().id(10L).code("ADMIN").build()));

        DataScope scope = dataScopeService.resolve();

        assertTrue(scope.all());
    }

    @Test
    void multipleRolesTakeWidestScope() {
        loginAs(1L);
        when(userService.getRoleIdsByUserId(1L)).thenReturn(List.of(10L, 11L));
        when(roleService.listByIds(anyCollection())).thenReturn(List.of(
                RoleEntity.builder().id(10L).code("R1").dataScope(RoleEntity.DATA_SCOPE_SELF).build(),
                RoleEntity.builder().id(11L).code("R2").dataScope(RoleEntity.DATA_SCOPE_ALL).build()));

        // 任一角色为"全部"即全域
        assertTrue(dataScopeService.resolve().all());
    }

    @Test
    void fiveTierScopesAggregateUnion() {
        loginAs(1L);
        when(userService.getRoleIdsByUserId(1L)).thenReturn(List.of(1L, 2L, 3L));
        when(roleService.listByIds(anyCollection())).thenReturn(List.of(
                RoleEntity.builder().id(1L).code("R1").dataScope(RoleEntity.DATA_SCOPE_CUSTOM).build(),
                RoleEntity.builder().id(2L).code("R2").dataScope(RoleEntity.DATA_SCOPE_DEPT_AND_CHILD).build(),
                RoleEntity.builder().id(3L).code("R3").dataScope(RoleEntity.DATA_SCOPE_SELF).build()));
        // 自定义档：R1 的部门 {10, 11}
        when(roleDeptMapper.selectDeptIdsByRoleId(1L)).thenReturn(List.of(10L, 11L));
        // 当前用户部门 5，本部门及以下档：子树 {5, 6}
        when(userService.getById(1L)).thenReturn(UserEntity.builder().deptId(5L).build());
        when(sysDeptService.listSubtreeIds(5L)).thenReturn(List.of(5L, 6L));

        DataScope scope = dataScopeService.resolve();

        assertFalse(scope.all());
        assertEquals(Set.of(10L, 11L, 5L, 6L), scope.deptIds());
        // 仅本人档：selfUserIds = 当前用户
        assertEquals(Set.of(1L), scope.selfUserIds());
    }

    @Test
    void customScopeOnlyAggregatesRoleDeptIds() {
        loginAs(1L);
        when(userService.getRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        when(roleService.listByIds(anyCollection()))
                .thenReturn(List.of(RoleEntity.builder().id(1L).code("R1").dataScope(RoleEntity.DATA_SCOPE_CUSTOM).build()));
        when(roleDeptMapper.selectDeptIdsByRoleId(1L)).thenReturn(List.of(10L));
        when(userService.getById(1L)).thenReturn(UserEntity.builder().deptId(null).build());

        DataScope scope = dataScopeService.resolve();

        assertEquals(Set.of(10L), scope.deptIds());
        assertTrue(scope.selfUserIds().isEmpty());
    }
}
