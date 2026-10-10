package com.gj.llm.base.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.base.entity.OperLogEntity;
import com.gj.llm.base.service.OperLogService;
import com.gj.llm.common.web.R;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志控制器 -- 审计日志查询（管理端）。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET /api/oper-logs — 分页查询</li>
 * </ul>
 *
 * <p>不做手动清空：审计日志的删除只走保留期定时任务（{@code LogCleanJob}，
 * {@code gj.llm.log.retention-days}），避免一键清空留下销毁审计记录的口子。</p>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/oper-logs")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    /**
     * 分页查询操作日志。
     *
     * @param page     页码（默认 1）
     * @param size     每页条数（默认 10）
     * @param module   操作模块（可空）
     * @param operator 操作人用户名（模糊，可空）
     * @param status   操作状态：1=成功 0=失败（可空）
     * @return 分页结果，按操作时间倒序
     */
    @GetMapping
    public R<IPage<OperLogEntity>> page(@RequestParam(defaultValue = "1") long page,
                                        @Max(value = 200, message = "每页条数最大 200")
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String module,
                                        @RequestParam(required = false) String operator,
                                        @RequestParam(required = false) Integer status) {
        return R.ok(operLogService.page(page, size, module, operator, status));
    }
}
