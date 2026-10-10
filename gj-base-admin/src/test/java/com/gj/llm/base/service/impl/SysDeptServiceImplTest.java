package com.gj.llm.base.service.impl;

import com.gj.llm.base.entity.SysDeptEntity;
import com.gj.llm.base.mapper.RoleDeptMapper;
import com.gj.llm.base.mapper.SysDeptMapper;
import com.gj.llm.base.model.DeptUpdateRequest;
import com.gj.llm.base.service.UserService;
import com.gj.llm.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysDeptServiceImpl} 单测 -- 覆盖换父自环/移入子孙拒绝与祖级链路前缀重写。
 *
 * @author gj-llm
 */
@ExtendWith(MockitoExtension.class)
class SysDeptServiceImplTest {

    @Mock private SysDeptMapper deptMapper;
    @Mock private UserService userService;
    @Mock private RoleDeptMapper roleDeptMapper;

    private SysDeptServiceImpl deptService;

    @BeforeEach
    void setUp() {
        deptService = new SysDeptServiceImpl(userService, roleDeptMapper);
        ReflectionTestUtils.setField(deptService, "baseMapper", deptMapper);
    }

    @Test
    void updateRejectsMovingDeptUnderItself() {
        when(deptMapper.selectById(5L))
                .thenReturn(SysDeptEntity.builder().id(5L).parentId(1L).ancestors("0,1").name("d5").build());

        DeptUpdateRequest request = new DeptUpdateRequest();
        request.setParentId(5L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deptService.update(5L, request));

        assertEquals("dept.cyclicMoveSelf", ex.getMessage());
        verify(deptMapper, never()).updateById(any(SysDeptEntity.class));
    }

    @Test
    void updateRejectsMovingDeptIntoOwnSubtree() {
        when(deptMapper.selectById(5L))
                .thenReturn(SysDeptEntity.builder().id(5L).parentId(1L).ancestors("0,1").name("d5").build());
        // 目标父 7 是 5 的子孙（祖先链路含 5）
        when(deptMapper.selectById(7L))
                .thenReturn(SysDeptEntity.builder().id(7L).parentId(5L).ancestors("0,1,5").name("d7").build());

        DeptUpdateRequest request = new DeptUpdateRequest();
        request.setParentId(7L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deptService.update(5L, request));

        assertEquals("dept.cyclicMove", ex.getMessage());
        verify(deptMapper, never()).updateById(any(SysDeptEntity.class));
    }

    @Test
    void updateRewritesDescendantAncestorsOnLegalReparent() {
        SysDeptEntity d8 = SysDeptEntity.builder().id(8L).parentId(1L).ancestors("0,1").name("d8").build();
        SysDeptEntity child9 = SysDeptEntity.builder().id(9L).parentId(8L).ancestors("0,1,8").name("d9").build();
        SysDeptEntity newParent = SysDeptEntity.builder().id(2L).parentId(0L).ancestors("0").name("d2").build();
        when(deptMapper.selectById(8L)).thenReturn(d8);
        when(deptMapper.selectById(2L)).thenReturn(newParent);
        when(deptMapper.selectList(any())).thenReturn(List.of(d8, child9, newParent));
        when(deptMapper.updateById(any(SysDeptEntity.class))).thenReturn(1);

        DeptUpdateRequest request = new DeptUpdateRequest();
        request.setParentId(2L);

        SysDeptEntity updated = deptService.update(8L, request);

        assertEquals("0,2", updated.getAncestors());
        // 自身 + 子孙各一次 updateById；子孙链路前缀重写为 "0,2,8"
        ArgumentCaptor<SysDeptEntity> captor = ArgumentCaptor.forClass(SysDeptEntity.class);
        verify(deptMapper, times(2)).updateById(captor.capture());
        List<String> ancestorsList = captor.getAllValues().stream().map(SysDeptEntity::getAncestors).toList();
        assertTrue(ancestorsList.contains("0,2"));
        assertTrue(ancestorsList.contains("0,2,8"));
    }
}
