package com.gj.llm.base.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.base.annotation.OperLog;
import com.gj.llm.base.entity.DictTypeEntity;
import com.gj.llm.base.model.DictTypeCreateRequest;
import com.gj.llm.base.model.DictTypeUpdateRequest;
import com.gj.llm.base.service.DictTypeService;
import com.gj.llm.common.web.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典类型控制器 -- 字典类型的增删改查（管理端）。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET    /api/dicts/types      - 分页查询</li>
 *   <li>POST   /api/dicts/types      - 创建</li>
 *   <li>PUT    /api/dicts/types/{id} - 更新（type 改名级联更新数据）</li>
 *   <li>DELETE /api/dicts/types/{id} - 删除（有数据时拒绝）</li>
 * </ul>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/dicts/types")
@RequiredArgsConstructor
public class DictTypeController {

    private final DictTypeService dictTypeService;

    /** 分页查询字典类型（keyword 匹配名称/类型编码） */
    @GetMapping
    public R<IPage<DictTypeEntity>> page(@RequestParam(defaultValue = "1") long page,
                                        @Max(value = 200, message = "每页条数最大 200")
                                        @RequestParam(defaultValue = "10") long size,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer status) {
        return R.ok(dictTypeService.page(page, size, keyword, status));
    }

    /** 创建字典类型 */
    @OperLog(module = "字典管理", type = "新增")
    @PostMapping
    public R<DictTypeEntity> create(@Valid @RequestBody DictTypeCreateRequest request) {
        return R.ok(dictTypeService.create(request), "字典类型创建成功");
    }

    /** 更新字典类型（type 改名级联更新字典数据） */
    @OperLog(module = "字典管理", type = "更新")
    @PutMapping("/{id}")
    public R<DictTypeEntity> update(@PathVariable Long id,
                                    @Valid @RequestBody DictTypeUpdateRequest request) {
        return R.ok(dictTypeService.update(id, request), "字典类型更新成功");
    }

    /** 删除字典类型 */
    @OperLog(module = "字典管理", type = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        dictTypeService.delete(id);
        return R.ok(null, "字典类型删除成功");
    }
}
