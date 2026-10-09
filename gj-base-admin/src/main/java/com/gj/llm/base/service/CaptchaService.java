package com.gj.llm.base.service;

import com.gj.llm.base.model.CaptchaResponse;

/**
 * 滑动验证码服务 —— 生成拼图与校验滑块位置。
 *
 * @author gj-llm
 */
public interface CaptchaService {

    /**
     * 生成一帧滑动验证码。
     *
     * <p>开关关闭时返回 {@code enabled=false}（前端隐藏滑块）。
     * 单 IP 每分钟生成次数受限（防刷）。验证码一次性令牌与缺口坐标存 Redis（TTL 见配置）。</p>
     *
     * @param clientIp 客户端 IP（生成限流用）
     * @return 验证码响应（含 Base64 图片与校验令牌）
     */
    CaptchaResponse generate(String clientIp);

    /**
     * 校验滑块位置（在登录流程最前调用，先于锁定检查）。
     *
     * <p>校验通过即消费（令牌作废），密码错误重试时需重新拉取验证码。
     * 失败累计达到上限后令牌作废，前端需刷新验证码。</p>
     *
     * @param captchaToken 验证码令牌
     * @param slideX       拼图块画布左缘的 x 坐标（前端拖动终值）
     * @throws RuntimeException 未通过验证 / 令牌过期 / 失败次数超限
     */
    void verify(String captchaToken, Integer slideX);
}
