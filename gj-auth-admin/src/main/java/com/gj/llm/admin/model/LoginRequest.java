package com.gj.llm.admin.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO。
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
}
