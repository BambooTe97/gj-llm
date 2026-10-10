package com.gj.llm.common.exception;

/**
 * 警告业务异常 -- 用户调整输入、刷新或稍后重试即可通过的业务拒绝
 * （如"用户名已存在"、"验证码已过期"）。
 *
 * <p>前端以黄色提示（Warn）呈现。业务模块可继续派生自己的异常类：</p>
 * <pre>{@code
 * public class UsernameExistsException extends WarnBusinessException {
 *     public UsernameExistsException(Object username) { super("user.usernameExists", username); }
 * }
 * }</pre>
 *
 * @author gj-llm
 */
public class WarnBusinessException extends BusinessException {

    public WarnBusinessException(String i18nKey, Object... args) {
        super(i18nKey, args);
    }

    public WarnBusinessException(Throwable cause, String i18nKey, Object... args) {
        super(cause, i18nKey, args);
    }

    @Override
    public String getLevel() {
        return LEVEL_WARN;
    }
}
