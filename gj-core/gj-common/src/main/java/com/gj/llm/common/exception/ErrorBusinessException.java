package com.gj.llm.common.exception;

/**
 * 严重业务异常 -- 数据不一致、系统故障、需人工介入等严重失败。
 *
 * <p>前端以红色通知（Error）呈现。业务模块可继续派生自己的异常类：</p>
 * <pre>{@code
 * public class QuotaExceededException extends ErrorBusinessException {
 *     public QuotaExceededException(Object quota) { super("chat.quotaExceeded", quota); }
 * }
 * }</pre>
 *
 * @author gj-llm
 */
public class ErrorBusinessException extends BusinessException {

    public ErrorBusinessException(String i18nKey, Object... args) {
        super(i18nKey, args);
    }

    public ErrorBusinessException(Throwable cause, String i18nKey, Object... args) {
        super(cause, i18nKey, args);
    }

    @Override
    public String getLevel() {
        return LEVEL_ERROR;
    }
}
