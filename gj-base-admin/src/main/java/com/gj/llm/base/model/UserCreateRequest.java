package com.gj.llm.base.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * 创建用户请求 DTO。
 *
 * @author gj-llm
 */
@Data
public class UserCreateRequest {

    /** 用户名（登录凭证），3-50 字符 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度为 3-50 个字符")
    private String username;

    /** 密码，最少 6 位 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度至少 6 位")
    private String password;

    /** 昵称 */
    @Size(max = 50, message = "昵称最长 50 个字符")
    private String nickname;

    /** 邮箱 */
    @Size(max = 100, message = "邮箱最长 100 个字符")
    private String email;

    /** 角色 ID 集合 */
    private Set<Long> roleIds;
}
