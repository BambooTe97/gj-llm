package com.gj.llm.base.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.base.annotation.OperLog;
import com.gj.llm.base.entity.SysConfigEntity;
import com.gj.llm.base.model.SysConfigCreateRequest;
import com.gj.llm.base.model.SysConfigUpdateRequest;
import com.gj.llm.base.service.SysConfigService;
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

/**
 * 参数配置控制器 -- 运行时可调参数的增删改查 + 业务侧按键消费入口。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET    /api/configs          - 分页查询</li>
 *   <li>GET    /api/configs/key/{key} - 按键名查询（全员消费，perm system:config:key）</li>
 *   <li>POST   /api/configs          - 创建</li>
 *   <li>PUT    /api/configs/{id}     - 更新（key 不可改）</li>
 *   <li>DELETE /api/configs/{id}     - 删除（内置参数拒绝）</li>
 * </ul>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;

    /** 分页查询参数配置（keyword 匹配名称/键名） */
    @GetMapping
    public R<IPage<SysConfigEntity>> page(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword) {
        return R.ok(sysConfigService.page(page, size, keyword));
    }

    /**
     * 按键名查询参数（业务侧消费入口：运行时可调配置）。
     *
     * @param key 参数键名
     * @return 参数实体（不存在时 data 为 null）
     */
    @GetMapping("/key/{key}")
    public R<SysConfigEntity> getByKey(@PathVariable String key) {
        return R.ok(sysConfigService.getByKey(key));
    }

    /** 创建参数 */
    @OperLog(module = "参数配置", type = "新增")
    @PostMapping
    public R<SysConfigEntity> create(@Valid @RequestBody SysConfigCreateRequest request) {
        return R.ok(sysConfigService.create(request), "参数创建成功");
    }

    /** 更新参数（configKey 不可修改） */
    @OperLog(module = "参数配置", type = "更新")
    @PutMapping("/{id}")
    public R<SysConfigEntity> update(@PathVariable Long id,
                                     @Valid @RequestBody SysConfigUpdateRequest request) {
        return R.ok(sysConfigService.update(id, request), "参数更新成功");
    }

    /** 删除参数（内置参数拒绝删除） */
    @OperLog(module = "参数配置", type = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysConfigService.delete(id);
        return R.ok(null, "参数删除成功");
    }
}
