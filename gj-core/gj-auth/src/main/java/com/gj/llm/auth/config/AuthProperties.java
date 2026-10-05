package com.gj.llm.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据授权配置 —— 可见域缓存、默认可见性与管理员角色。
 *
 * <p>配置前缀 {@code gj.llm.auth}，与 {@code RagProperties} 同风格（{@code @Component} + {@code @ConfigurationProperties}）。</p>
 *
 * @author gj-llm
 */
@Data
@Component
@ConfigurationProperties(prefix = "gj.llm.auth")
public class AuthProperties {

    /** 可见性：全员可见（新建库默认值，保持既有行为） */
    public static final String VISIBILITY_PUBLIC = "PUBLIC";

    /** 可见性：仅 owner + 授权主体可见 */
    public static final String VISIBILITY_RESTRICTED = "RESTRICTED";

    /** 主体类型：用户 */
    public static final String PRINCIPAL_USER = "user";

    /** 主体类型：角色 */
    public static final String PRINCIPAL_ROLE = "role";

    /** 主体类型：部门（预留，用户体系暂无部门概念） */
    public static final String PRINCIPAL_DEPT = "dept";

    /** 角色/授权/可见集 Redis 缓存 TTL（秒）——兜底，授权变更走主动失效 */
    private int cacheTtlSeconds = 60;

    /** 新建知识库默认可见性：PUBLIC（全员可见，商用化时可切 RESTRICTED） */
    private String defaultVisibility = VISIBILITY_PUBLIC;

    /** 管理员角色编码（命中即全库可见可管），大小写不敏感 */
    private List<String> adminRoles = new ArrayList<>(List.of("ADMIN"));

    /** 判定角色编码是否为管理员角色 */
    public boolean isAdminRole(String roleCode) {
        return roleCode != null && adminRoles.stream().anyMatch(r -> r.equalsIgnoreCase(roleCode));
    }
}
