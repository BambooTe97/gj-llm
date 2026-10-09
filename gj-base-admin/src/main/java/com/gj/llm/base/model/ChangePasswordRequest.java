package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自助修改密码请求。
 *
 * <p>新密码的复杂度在服务层由 {@code PasswordPolicyValidator} 统一校验（与创建/重置同策略），
 * DTO 层仅做非空与长度上限兜底。</p>
 *
 * @author gj-llm
 */
@Data
public class ChangePasswordRequest {

    /** 原密码（用于本人身份确认） */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(max = 100, message = "密码长度不能超过 100 位")
    private String newPassword;
}
