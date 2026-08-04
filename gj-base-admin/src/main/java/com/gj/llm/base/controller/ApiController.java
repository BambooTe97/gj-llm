package com.gj.llm.base.controller;

import com.gj.llm.base.entity.ApiEntity;
import com.gj.llm.base.service.ApiService;
import com.gj.llm.common.web.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 接口管理控制器 -- 提供接口列表查询，供菜单管理页配置"按钮-接口"关联时选择。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET /api/apis - 有效接口列表（按路径升序）</li>
 * </ul>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/apis")
@RequiredArgsConstructor
public class ApiController {

    private final ApiService apiService;

    /** 获取全部有效接口（供菜单关联接口选择） */
    @GetMapping
    public R<List<ApiEntity>> list() {
        return R.ok(apiService.listActive());
    }
}
