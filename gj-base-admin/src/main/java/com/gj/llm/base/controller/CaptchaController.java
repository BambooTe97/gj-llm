package com.gj.llm.base.controller;

import com.gj.llm.base.model.CaptchaResponse;
import com.gj.llm.base.service.CaptchaService;
import com.gj.llm.base.util.WebUtils;
import com.gj.llm.common.web.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 滑动验证码控制器。
 *
 * <p>登录前调用：Security 层 ignore-paths 与 ApiPermissionInterceptor WHITELIST 均已放行
 * {@code /api/captcha/**}。不加 @OperLog（高频匿名请求，避免日志噪音）。</p>
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /**
     * 生成滑动验证码。
     *
     * <p>开关关闭时返回 {@code enabled=false}，前端据此隐藏滑块。</p>
     *
     * @param request 当前请求（取客户端 IP 做生成限流）
     * @return 验证码响应（Base64 背景图/拼图块 + 令牌）
     */
    @GetMapping("/generate")
    public R<CaptchaResponse> generate(HttpServletRequest request) {
        return R.ok(captchaService.generate(WebUtils.getClientIp(request)));
    }
}
