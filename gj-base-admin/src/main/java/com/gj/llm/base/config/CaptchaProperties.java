package com.gj.llm.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 滑动验证码配置 —— 映射 {@code application.yaml} 中 {@code gj.llm.captcha} 前缀。
 *
 * <p>验证码为自研实现：Java2D 生成拼图 + Redis 存缺口坐标校验（零第三方依赖）。
 * 开关唯一真源在后端：{@code enabled=false} 时 generate 返回 {@code enabled:false}，
 * 前端隐藏滑块，登录不校验。</p>
 *
 * @author gj-llm
 */
@Data
@Component
@ConfigurationProperties(prefix = "gj.llm.captcha")
public class CaptchaProperties {

    /** 是否启用滑动验证码（关闭后登录不校验，前端不渲染滑块） */
    private boolean enabled = true;

    /** 验证码有效期（秒），超时需刷新 */
    private int expireSeconds = 120;

    /** 滑块位置校验容差（像素） */
    private int tolerancePx = 5;

    /** 单个验证码最大失败尝试次数，达到后作废并要求刷新 */
    private int maxAttempts = 3;

    /** 单 IP 每分钟最大生成次数（防刷） */
    private int genLimitPerMinute = 30;
}
