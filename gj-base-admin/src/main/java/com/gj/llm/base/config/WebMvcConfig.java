package com.gj.llm.base.config;

import com.gj.llm.base.interceptor.ApiPermissionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 -- 注册接口权限拦截器。
 *
 * <p>拦截所有路径，{@code /open/**}（免登录）与 {@code /error}（内置）由 WebMvc 排除；
 * 其余白名单（login/refresh/userinfo/logout）在拦截器内部判断放行。</p>
 *
 * @author gj-llm
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiPermissionInterceptor apiPermissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiPermissionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/open/**", "/error", "/favicon.ico");
    }
}
