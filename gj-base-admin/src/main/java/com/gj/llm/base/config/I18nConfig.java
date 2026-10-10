package com.gj.llm.base.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

/**
 * i18n 配置 —— 语言跟随请求 Accept-Language 头，缺省简体中文。
 *
 * <p>DispatcherServlet 把解析结果填充进 {@code LocaleContextHolder}，
 * GlobalExceptionHandler 据此解析业务异常文案；异步线程（netty/MCP）无请求
 * 上下文时由启动类统一回落 JVM 默认 Locale（见 GjLlmApplication）。</p>
 *
 * @author gj-llm
 */
@Configuration
public class I18nConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        return resolver;
    }
}
