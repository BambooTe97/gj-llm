package com.gj.llm.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置 -- 独立配置 {@link PasswordEncoder} bean。
 *
 * <p>刻意从 {@link SecurityConfig} 中拆出，避免 {@code SecurityConfig}（依赖 JwtAuthenticationFilter）
 * 与需要 PasswordEncoder 的 Service（如 UserServiceImpl）之间形成循环依赖。</p>
 *
 * @author gj-llm
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * 密码编码器 -- BCrypt 不可逆哈希。
     *
     * <p>BCrypt 自动处理盐值（salt）：每次 encode() 结果不同，
     * 通过 matches(rawPassword, encodedPassword) 进行比对。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
