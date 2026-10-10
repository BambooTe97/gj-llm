package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求 DTO。
 *
 * @author gj-llm
 */
@Data
public class ResetPasswordRequest {

    /** 新密码（BCrypt 加密后存储；长度下限与 PasswordPolicyValidator 强度策略一致） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 100, message = "密码长度至少 8 位")
    private String newPassword;
}
