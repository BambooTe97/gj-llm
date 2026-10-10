package com.gj.llm.base.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.base.annotation.OperLog;
import com.gj.llm.base.model.NotifyAdminVO;
import com.gj.llm.base.model.NotifyVO;
import com.gj.llm.base.service.NotifyService;
import com.gj.llm.common.util.StringUtils;
import com.gj.llm.common.web.R;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通知管理控制器 —— 通知管理中心（系统管理页）的后端：全量分页、定向发送、删除。
 *
 * <p>权限点 {@code notify:manage}（与接收端 {@code notify:view} 分离：
 * 普通用户只见铃铛，管理者才有发送/治理入口）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notify/admin")
@RequiredArgsConstructor
public class NotifyAdminController {

    private final NotifyService notifyService;

    /** 全量通知分页（标题/内容模糊 + 级别 + 已读过滤） */
    @GetMapping
    public R<IPage<NotifyAdminVO>> page(
            @RequestParam(defaultValue = "1") long page,
                                        @Max(value = 200, message = "每页条数最大 200")
                                        @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Integer readFlag) {
        return R.ok(notifyService.pageForAdmin(page, size, keyword, level, readFlag));
    }

    /**
     * 定向发送通知给指定用户 —— 管理端的发送入口（先落库后推送）。
     */
    @OperLog(module = "通知管理", type = "发送")
    @PostMapping("/send")
    public R<NotifyVO> send(@RequestBody Map<String, String> body) {
        Long userId = Long.valueOf(body.get("userId"));
        String title = body.getOrDefault("title", "");
        String content = body.getOrDefault("content", "");
        if (StringUtils.isBlank(title)) {
            return R.badRequest("通知标题不能为空");
        }
        NotifyVO vo = notifyService.createAndPush(userId, title, content, body.get("level"));
        return R.ok(vo, "通知已发送（在线实时推送，离线落库待拉取）");
    }

    /** 删除通知（物理删除） */
    @OperLog(module = "通知管理", type = "删除")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        notifyService.removeById(id);
        return R.ok(null, "已删除");
    }
}
