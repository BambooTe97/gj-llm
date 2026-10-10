package com.gj.llm.base.util;

import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.common.exception.WarnBusinessException;
import com.gj.llm.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 密码复杂度策略校验器 —— 创建用户、管理员重置密码、自助修改密码的统一校验入口。
 *
 * <p>规则（可通过 {@code gj.llm.auth.password-policy} 配置）：</p>
 * <ul>
 *   <li>长度区间 [minLength, maxLength]，默认 [8, 100]</li>
 *   <li>大写字母 / 小写字母 / 数字 / 特殊字符 四类中至少包含 {@code minCategories} 类（默认 3）</li>
 *   <li>禁止包含用户名（忽略大小写，默认开启）</li>
 * </ul>
 *
 * <p>校验失败抛 {@link WarnBusinessException}，由 {@code GlobalExceptionHandler} 转为 400 + 可读文案。
 * 多条规则提示需先按当前 Locale 解析并拼接后作为占位参数传入（{@code password.policy.violation}）。</p>
 *
 * @author gj-llm
 */
@Component
@RequiredArgsConstructor
public class PasswordPolicyValidator {

    /** 四类字符的判定正则 */
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    private final AuthProperties authProperties;
    private final MessageSource messageSource;

    /**
     * 校验明文密码是否符合复杂度策略。
     *
     * @param rawPassword 明文密码
     * @param username    所属用户名（用于"禁止包含用户名"检查，可空）
     * @throws RuntimeException 任一规则不满足时抛出，消息为逐条可读文案（分号连接）
     */
    public void validate(String rawPassword, String username) {
        AuthProperties.PasswordPolicy policy = authProperties.getPasswordPolicy();
        if (policy == null || !policy.isEnabled() || rawPassword == null) {
            return;
        }

        List<String> errors = new ArrayList<>();
        if (rawPassword.length() < policy.getMinLength()) {
            errors.add(messageSource.getMessage("password.policy.minLength",
                    new Object[]{policy.getMinLength()}, LocaleContextHolder.getLocale()));
        }
        if (rawPassword.length() > policy.getMaxLength()) {
            errors.add(messageSource.getMessage("password.policy.maxLength",
                    new Object[]{policy.getMaxLength()}, LocaleContextHolder.getLocale()));
        }

        int categories = 0;
        if (UPPERCASE.matcher(rawPassword).find()) {
            categories++;
        }
        if (LOWERCASE.matcher(rawPassword).find()) {
            categories++;
        }
        if (DIGIT.matcher(rawPassword).find()) {
            categories++;
        }
        if (SPECIAL.matcher(rawPassword).find()) {
            categories++;
        }
        if (categories < policy.getMinCategories()) {
            errors.add(messageSource.getMessage("password.policy.minCategories",
                    new Object[]{policy.getMinCategories()}, LocaleContextHolder.getLocale()));
        }

        if (policy.isForbidUsername() && StringUtils.isNotBlank(username)
                && rawPassword.toLowerCase().contains(username.toLowerCase())) {
            errors.add(messageSource.getMessage("password.policy.forbidUsername",
                    null, LocaleContextHolder.getLocale()));
        }

        if (!errors.isEmpty()) {
            throw new WarnBusinessException("password.policy.violation", String.join("；", errors));
        }
    }
}
