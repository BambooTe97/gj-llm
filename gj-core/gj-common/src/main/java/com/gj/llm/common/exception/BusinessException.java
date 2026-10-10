package com.gj.llm.common.exception;

/**
 * 业务异常基类 -- service 层对外抛出的可预期业务失败，由
 * {@code GlobalExceptionHandler} 统一解析 i18n 文案并映射为 HTTP 400。
 *
 * <p>异常与业务绑定：携带 i18n key 与占位参数（如 {@code new XxxException("user.notFound", id)}），
 * 文案由 MessageSource 按请求 Locale 解析，key 缺失时兜底返回 key 本身。</p>
 *
 * <p>严重程度由子类决定，前端据此选择提示形态：</p>
 * <ul>
 *   <li>{@link ErrorBusinessException}：严重失败（数据不一致/系统故障/需人工介入）→ 前端红色通知</li>
 *   <li>{@link WarnBusinessException}：轻微警告（用户改输入/刷新即可通过）→ 前端黄色提示</li>
 * </ul>
 *
 * <p>与 {@link RuntimeException} 兜底的边界：框架/数据访问异常
 * （如 {@code DataAccessException}）不是业务异常，由全局处理器归入 500 兜底，
 * 避免数据库细节外泄给客户端。</p>
 *
 * @author gj-llm
 */
public abstract class BusinessException extends RuntimeException {

    /** 前端严重错误（红色通知） */
    public static final String LEVEL_ERROR = "error";

    /** 前端轻微警告（黄色提示） */
    public static final String LEVEL_WARN = "warn";

    /** i18n 消息 key，如 {@code user.notFound} */
    private final String i18nKey;

    /** MessageFormat 占位参数，对应文案中的 {0}、{1}… */
    private final transient Object[] args;

    protected BusinessException(String i18nKey, Object... args) {
        super(i18nKey);
        this.i18nKey = i18nKey;
        this.args = args;
    }

    protected BusinessException(Throwable cause, String i18nKey, Object... args) {
        super(i18nKey, cause);
        this.i18nKey = i18nKey;
        this.args = args;
    }

    public String getI18nKey() {
        return i18nKey;
    }

    public Object[] getArgs() {
        return args;
    }

    /** 前端提示分级：{@link #LEVEL_ERROR} 或 {@link #LEVEL_WARN} */
    public abstract String getLevel();
}
