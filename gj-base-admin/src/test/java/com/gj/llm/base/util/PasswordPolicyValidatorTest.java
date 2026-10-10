package com.gj.llm.base.util;

import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PasswordPolicyValidator} 单测 -- 覆盖长度/类别数/禁含用户名与策略开关。
 *
 * @author gj-llm
 */
class PasswordPolicyValidatorTest {

    private AuthProperties authProperties;
    private PasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        // 与生产入口一致的确定 Locale（生产由 GjLlmApplication @PostConstruct 固定）
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        authProperties = new AuthProperties();
        validator = new PasswordPolicyValidator(authProperties, messageSource());
    }

    /** 真实词条 bundle（读 main 资源），与生产同源 */
    private static MessageSource messageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("i18n/base_messages");
        ms.setDefaultEncoding("UTF-8");
        return ms;
    }

    @Test
    void weakPasswordRejectedWithReadableReasons() {
        // "abc"：长度不足 + 类别数不足，两条原因合并为 violation 的 {0} 占位参数
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("abc", "user"));

        assertEquals("password.policy.violation", ex.getI18nKey());
        String joined = (String) ex.getArgs()[0];
        assertTrue(joined.contains("至少 8 位"));
        assertTrue(joined.contains("至少 3 类"));
    }

    @Test
    void passwordContainingUsernameRejected() {
        // 类别数/长度达标，但包含用户名（忽略大小写）
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("Alicexyz9!", "alice"));

        String joined = (String) ex.getArgs()[0];
        assertTrue(joined.contains("密码不能包含用户名"));
    }

    @Test
    void strongPasswordAccepted() {
        assertDoesNotThrow(() -> validator.validate("Str0ng!Pass", "bob"));
    }

    @Test
    void disabledPolicyAcceptsWeakPassword() {
        authProperties.getPasswordPolicy().setEnabled(false);

        assertDoesNotThrow(() -> validator.validate("abc", "user"));
    }

    @Test
    void nullPasswordAcceptedByValidator() {
        // null 交给 DTO 层 @NotBlank 兜底，校验器直接放行
        assertDoesNotThrow(() -> validator.validate(null, "user"));
    }
}
