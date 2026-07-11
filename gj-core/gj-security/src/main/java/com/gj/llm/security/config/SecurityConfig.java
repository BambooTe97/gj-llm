package com.gj.llm.security.config;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.security.filter.JwtAuthenticationFilter;
import com.gj.llm.security.properties.SecurityProperties;
import com.gj.llm.security.service.SecurityUserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Spring Security 核心配置类 —— 定义安全过滤器链、认证提供者、密码编码器等核心 Bean。
 *
 * <p>使用 Spring Security 7.1 的 Lambda DSL 风格配置。</p>
 *
 * <h3>安全策略要点</h3>
 * <ul>
 *   <li><b>无状态会话</b>：{@code SessionCreationPolicy.STATELESS}，不创建 HttpSession</li>
 *   <li><b>CSRF 禁用</b>：前后端分离 + JWT 无状态架构下不存在 CSRF 风险</li>
 *   <li><b>白名单机制</b>：登录、刷新 Token 等接口无需认证，其余全部拦截</li>
 *   <li><b>JWT 前置过滤</b>：在 {@link UsernamePasswordAuthenticationFilter} 之前执行</li>
 *   <li><b>BCrypt 加密</b>：密码存储使用 BCrypt 不可逆哈希</li>
 * </ul>
 *
 * @author gj-llm
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityUserService securityUserService;
    private final SecurityProperties securityProperties;

    /**
     * 安全过滤器链 —— Spring Security 7.x 的核心配置入口。
     *
     * <p>替代了已废弃的 {@code WebSecurityConfigurerAdapter} 继承方式。</p>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 白名单路径：从配置属性读取，无需认证即可访问
        String[] ignorePaths = securityProperties.getIgnorePaths()
                .toArray(String[]::new);

        http
                // 禁用 CSRF：前后端分离 + JWT 无状态不存在 CSRF 风险
                .csrf(AbstractHttpConfigurer::disable)
                // 无状态会话策略：不创建 HttpSession，不生成 JSESSIONID Cookie
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // SecurityContext 存储策略：使用 Request 属性存储，
                // 确保异步 dispatch（如 SSE 流式响应）时认证信息不丢失
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository()))
                // 请求授权规则
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ignorePaths).permitAll()   // 白名单放行
                        .anyRequest().authenticated()                // 其余全部需要认证
                )
                // 在 Spring Security 内置认证过滤器之前插入 JWT 过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 注册自定义认证提供者（DAO 模式 + BCrypt）
                .authenticationProvider(authenticationProvider())
                // 异常处理：未认证返回 JSON 格式 401，与前端 ApiResponse 契约对齐
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write(JacksonUtils.toJson(
                                    Map.of("code", 401, "data", null, "message", "认证失败，请重新登录"))
                            );
                        })
                );

        return http.build();
    }

    /**
     * DAO 认证提供者 —— 通过 {@link SecurityUserService} 从数据库加载用户信息，
     * 使用 BCrypt 比对密码。
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        // 将 SecurityUserService 适配为 Spring Security 标准的 UserDetailsService
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        // 隐藏"用户不存在"异常，统一返回"用户名或密码错误"
        // 防止攻击者通过错误信息差异枚举系统用户
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    /**
     * 将 {@link SecurityUserService} 适配为 Spring Security 的 {@link UserDetailsService}。
     *
     * <p>若未找到用户则抛出 {@code UsernameNotFoundException}，
     * 由 {@link DaoAuthenticationProvider} 处理为认证失败。</p>
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> securityUserService.findByUsername(username)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(
                        "用户不存在: " + username));
    }

    /**
     * 密码编码器 —— BCrypt 不可逆哈希。
     *
     * <p>BCrypt 自动处理盐值（salt）：每次 encode() 结果不同，
     * 通过 matches(rawPassword, encodedPassword) 进行比对。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器 —— 供 Controller 层手动调用以执行用户名/密码认证。
     *
     * <p>典型用法：{@code authenticationManager.authenticate(
     * new UsernamePasswordAuthenticationToken(username, password))}</p>
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * SecurityContext 存储策略 —— 使用 HttpServletRequest 属性存储。
     *
     * <p>为什么不用默认的 HttpSession：{@link SessionCreationPolicy#STATELESS}
     * 不创建 Session，HttpSession 方案无法保存 Context。
     * 而 {@link RequestAttributeSecurityContextRepository} 将 Context
     * 保存在 HttpServletRequest 属性中，跨异步 dispatch 依然可用。</p>
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new RequestAttributeSecurityContextRepository();
    }
}
