package com.gj.llm.base.controller;

import com.gj.llm.base.annotation.OperLog;
import com.gj.llm.base.entity.SysDeptEntity;
import com.gj.llm.base.model.DeptCreateRequest;
import com.gj.llm.base.model.DeptUpdateRequest;
import com.gj.llm.base.service.SysDeptService;
import com.gj.llm.common.web.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门管理控制器 -- 部门树查询与增删改。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET    /api/depts/tree   - 部门树</li>
 *   <li>POST   /api/depts        - 创建部门</li>
 *   <li>PUT    /api/depts/{id}   - 更新部门（可换父）</li>
 *   <li>DELETE /api/depts/{id}   - 删除部门</li>
 * </ul>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/depts")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService sysDeptService;

    /** 部门树（全量，按 sort 升序） */
    @GetMapping("/tree")
    public R<List<SysDeptEntity>> tree() {
        return R.ok(sysDeptService.tree());
    }

    /** 创建部门 */
    @OperLog(module = "部门管理", type = "新增")
    @PostMapping
    public R<SysDeptEntity> create(@Valid @RequestBody DeptCreateRequest request) {
        return R.ok(sysDeptService.create(request), "部门创建成功");
    }

    /** 更新部门（parentId 变更时自动重算祖级链路） */
    @OperLog(module = "部门管理", type = "更新")
    @PutMapping("/{id}")
    public R<SysDeptEntity> update(@PathVariable Long id,
                                   @Valid @RequestBody DeptUpdateRequest request) {
        return R.ok(sysDeptService.update(id, request), "部门更新成功");
    }

    /** 删除部门（有下级或挂有用户时拒绝） */
    @OperLog(module = "部门管理", type = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysDeptService.delete(id);
        return R.ok(null, "部门删除成功");
    }
}
