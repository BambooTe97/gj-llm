package com.gj.llm;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Locale;

/**
 * gj-llm 应用入口。
 *
 * @author gj-llm
 */
@SpringBootApplication
public class GjLlmApplication {

    public static void main(String[] args) {
        SpringApplication.run(GjLlmApplication.class, args);
    }

    /**
     * 固定 JVM 默认 Locale 为简体中文 —— netty/MCP 等异步线程无请求上下文时，
     * {@code LocaleContextHolder} 回落该默认值，保证文案解析结果确定。
     */
    @PostConstruct
    public void initDefaultLocale() {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
    }

}
