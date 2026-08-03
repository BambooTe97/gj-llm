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

    /** 新密码（BCrypt 加密后存储） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度至少 6 位")
    private String newPassword;
}
