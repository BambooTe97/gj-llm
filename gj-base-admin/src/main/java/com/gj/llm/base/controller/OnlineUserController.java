package com.gj.llm.base.controller;

import com.gj.llm.base.model.OnlineUserVO;
import com.gj.llm.base.service.OnlineUserService;
import com.gj.llm.common.web.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线用户控制器 -- 在线会话查看与强制下线（管理端）。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET    /api/online/users           — 在线会话列表</li>
 *   <li>DELETE /api/online/users/{tokenId} — 强制下线指定会话</li>
 * </ul>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/online/users")
@RequiredArgsConstructor
public class OnlineUserController {

    private final OnlineUserService onlineUserService;

    /**
     * 在线会话列表（按登录时间倒序，不分页）。
     */
    @GetMapping
    public R<List<OnlineUserVO>> list() {
        return R.ok(onlineUserService.list());
    }

    /**
     * 强制下线指定会话（双 Token 入黑名单，立即生效）。
     *
     * @param tokenId 会话标识
     */
    @DeleteMapping("/{tokenId}")
    public R<Void> forceLogout(@PathVariable String tokenId) {
        onlineUserService.forceLogout(tokenId);
        return R.ok(null, "强制下线成功");
    }
}
