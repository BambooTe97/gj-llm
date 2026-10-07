package com.gj.llm.base.controller;

import com.gj.llm.base.model.NotifyVO;
import com.gj.llm.base.service.NotifyService;
import com.gj.llm.common.util.SecurityUtils;
import com.gj.llm.common.web.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息通知控制器 —— 列表 / 未读数 / 已读管理 / 测试发送。
 *
 * <p>send-test 是 gj-netty 端到端验证的入口：HTTP 落库 + WS 实时推送，
 * 一次调用同时覆盖两条通道。</p>
 *
 * @author gj-llm
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    /** 当前用户的通知列表（最近 50 条） */
    @GetMapping
    public R<List<NotifyVO>> list() {
        return R.ok(notifyService.listByUser());
    }

    /** 当前用户的未读数 */
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(notifyService.unreadCount());
    }

    /** 标记单条已读 */
    @PostMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notifyService.markRead(id);
        return R.ok(null, "已标记已读");
    }

    /** 全部标记已读 */
    @PostMapping("/read-all")
    public R<Map<String, Integer>> markAllRead() {
        return R.ok(Map.of("updated", notifyService.markAllRead()), "已全部标记已读");
    }

    /**
     * 发送测试通知（发给当前登录用户）—— 端到端验证入口。
     *
     * <p>WS 在线：落库 + 实时推送（前端弹 toast）；离线：仅落库，上线后列表可见。</p>
     */
    @PostMapping("/send-test")
    public R<NotifyVO> sendTest(@RequestBody(required = false) Map<String, String> body) {
        String title = body != null && body.get("title") != null && !body.get("title").isBlank()
                ? body.get("title") : "测试通知";
        String content = body != null && body.get("content") != null && !body.get("content").isBlank()
                ? body.get("content") : "这是一条通过 gj-netty 长连接基座推送的测试通知";
        String level = body != null ? body.get("level") : "info";
        NotifyVO vo = notifyService.createAndPush(SecurityUtils.getCurrentUserId(), title, content, level);
        return R.ok(vo, "通知已发送（在线实时推送，离线落库待拉取）");
    }
}
