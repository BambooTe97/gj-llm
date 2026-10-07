package com.gj.llm.base.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.base.annotation.OperLog;
import com.gj.llm.base.entity.DictDataEntity;
import com.gj.llm.base.model.DictDataCreateRequest;
import com.gj.llm.base.model.DictDataUpdateRequest;
import com.gj.llm.base.service.DictDataService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典数据控制器 -- 字典数据增删改查 + 业务侧按类型消费入口。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET    /api/dicts/datas           - 分页查询（按 dictType）</li>
 *   <li>GET    /api/dicts/datas/type/{type} - 按类型查询启用数据（全员消费，perm system:dict:data）</li>
 *   <li>POST   /api/dicts/datas           - 创建</li>
 *   <li>PUT    /api/dicts/datas/{id}      - 更新</li>
 *   <li>DELETE /api/dicts/datas/{id}      - 删除</li>
 * </ul>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/dicts/datas")
@RequiredArgsConstructor
public class DictDataController {

    private final DictDataService dictDataService;

    /** 分页查询字典数据（按字典类型） */
    @GetMapping
    public R<IPage<DictDataEntity>> page(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long size,
                                         @RequestParam String dictType,
                                         @RequestParam(required = false) String keyword) {
        return R.ok(dictDataService.page(page, size, dictType, keyword));
    }

    /**
     * 按类型查询启用的字典数据（业务侧消费入口：下拉框/标签渲染等，按 sort 升序）。
     *
     * @param type 字典类型编码
     * @return 启用状态的字典数据列表
     */
    @GetMapping("/type/{type}")
    public R<List<DictDataEntity>> listByType(@PathVariable String type) {
        return R.ok(dictDataService.listByType(type));
    }

    /** 创建字典数据 */
    @OperLog(module = "字典管理", type = "新增")
    @PostMapping
    public R<DictDataEntity> create(@Valid @RequestBody DictDataCreateRequest request) {
        return R.ok(dictDataService.create(request), "字典数据创建成功");
    }

    /** 更新字典数据 */
    @OperLog(module = "字典管理", type = "更新")
    @PutMapping("/{id}")
    public R<DictDataEntity> update(@PathVariable Long id,
                                    @Valid @RequestBody DictDataUpdateRequest request) {
        return R.ok(dictDataService.update(id, request), "字典数据更新成功");
    }

    /** 删除字典数据 */
    @OperLog(module = "字典管理", type = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        dictDataService.delete(id);
        return R.ok(null, "字典数据删除成功");
    }
}
