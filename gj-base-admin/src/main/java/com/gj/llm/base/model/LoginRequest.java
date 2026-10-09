package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO。
 *
 * <p>滑动验证码开启时，{@code captchaToken} 与 {@code slideX} 必填；
 * 关闭时可缺省（服务层按配置判断，不加 @NotBlank 以兼容开关切换）。</p>
 *
 * @author gj-llm
 */
@Data
public class LoginRequest {

    /** 用户名，不能为空 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码，不能为空 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 滑动验证码令牌（验证码开启时必填，来自 /api/captcha/generate） */
    private String captchaToken;

    /** 滑块位置：拼图块画布左缘的 x 坐标（验证码开启时必填） */
    private Integer slideX;
}
