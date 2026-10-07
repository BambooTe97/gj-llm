package com.gj.llm.base.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.SysDeptEntity;
import com.gj.llm.base.model.DeptCreateRequest;
import com.gj.llm.base.model.DeptUpdateRequest;

import java.util.List;

/**
 * 部门服务 -- 部门树管理。
 *
 * @author gj-llm
 */
public interface SysDeptService extends IService<SysDeptEntity> {

    /**
     * 部门树（全量，按 sort 升序）。
     *
     * @return 树形结构（顶级节点列表，children 递归挂载）
     */
    List<SysDeptEntity> tree();

    /**
     * 创建部门（父部门须存在；ancestors 自动计算）。
     *
     * @param request 创建请求
     * @return 创建后的实体
     */
    SysDeptEntity create(DeptCreateRequest request);

    /**
     * 更新部门；换父时校验新父存在且不得移入自身子孙，并重算自身与子孙的祖级链路。
     *
     * @param id      部门 ID
     * @param request 更新请求
     * @return 更新后的实体
     */
    SysDeptEntity update(Long id, DeptUpdateRequest request);

    /**
     * 删除部门；存在下级部门或挂有用户时拒绝。
     *
     * @param id 部门 ID
     */
    void delete(Long id);

    /**
     * 查询部门子树 ID 集合（含自身）—— 供用户列表按部门（含下级）过滤。
     *
     * @param deptId 部门 ID
     * @return 子树内全部部门 ID（含自身）；部门不存在时返回仅含自身的单元素列表
     */
    List<Long> listSubtreeIds(Long deptId);
}
