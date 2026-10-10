package com.gj.llm.base.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.base.entity.LogininforEntity;
import com.gj.llm.base.service.LoginLogService;
import com.gj.llm.common.web.R;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志控制器 -- 登录审计查询（管理端）。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET /api/login-logs — 分页查询</li>
 * </ul>
 *
 * <p>权限四件套：perms {@code system:loginlog:list}，
 * ApiAutoLinker 按控制器名自动关联（见 init/ApiAutoLinker RULES）。
 * 不做手动清空：审计日志的删除只走保留期定时任务（{@code LogCleanJob}，
 * {@code gj.llm.log.retention-days}），避免一键清空留下销毁审计记录的口子。</p>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    /**
     * 分页查询登录日志。
     *
     * @param page     页码（默认 1）
     * @param size     每页条数（默认 10）
     * @param username 登录账号（模糊，可空）
     * @param ip       客户端 IP（模糊，可空）
     * @param status   登录状态：1=成功 0=失败（可空）
     * @return 分页结果，按登录时间倒序
     */
    @GetMapping
    public R<IPage<LogininforEntity>> page(@RequestParam(defaultValue = "1") long page,
                                        @Max(value = 200, message = "每页条数最大 200")
                                        @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String username,
                                           @RequestParam(required = false) String ip,
                                           @RequestParam(required = false) Integer status) {
        return R.ok(loginLogService.page(page, size, username, ip, status));
    }
}
