package com.gj.llm.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据授权配置 —— 资源 ACL 的缓存 TTL 与管理员角色。
 *
 * <p>配置前缀 {@code gj.llm.auth}（数据授权语义，与登录认证 {@code AuthService} 区分）。</p>
 *
 * @author gj-llm
 */
@Data
@Component
@ConfigurationProperties(prefix = "gj.llm.auth")
public class AuthProperties {

    /** 可见性：全员可见（新建资源默认值，保持既有行为） */
    public static final String VISIBILITY_PUBLIC = "PUBLIC";

    /** 可见性：仅 owner + 授权主体可见 */
    public static final String VISIBILITY_RESTRICTED = "RESTRICTED";

    /** 主体类型：用户 */
    public static final String PRINCIPAL_USER = "user";

    /** 主体类型：角色 */
    public static final String PRINCIPAL_ROLE = "role";

    /** 主体类型：部门（预留，用户体系暂无部门概念） */
    public static final String PRINCIPAL_DEPT = "dept";

    /** 角色/授权缓存 TTL（秒）——兜底，授权变更走主动失效 */
    private int cacheTtlSeconds = 60;

    /** 新建知识库默认可见性：PUBLIC（全员可见，商用化时可切 RESTRICTED） */
    private String defaultVisibility = VISIBILITY_PUBLIC;

    /** 登录失败次数上限（滑动窗口内累计，达到即锁定），0=不启用锁定 */
    private int maxLoginFailures = 5;

    /** 登录失败计数滑动窗口与账号锁定时长（分钟） */
    private int lockDurationMinutes = 30;

    /** 管理员角色编码（命中即全库可见可管），大小写不敏感 */
    private List<String> adminRoles = new ArrayList<>(List.of("ADMIN"));

    /** 密码复杂度策略（创建用户/管理员重置/自助修改密码统一校验） */
    private PasswordPolicy passwordPolicy = new PasswordPolicy();

    /** 判定角色编码是否为管理员角色 */
    public boolean isAdminRole(String roleCode) {
        return roleCode != null && adminRoles.stream().anyMatch(r -> r.equalsIgnoreCase(roleCode));
    }

    /**
     * 密码复杂度策略配置。
     *
     * <p>规则：长度区间 + 大写/小写/数字/特殊字符四类中至少 N 类 + 可选禁止包含用户名。
     * 校验入口统一在 {@code PasswordPolicyValidator}。</p>
     */
    @Data
    public static class PasswordPolicy {

        /** 是否启用复杂度校验（关闭后仅保留 DTO 层长度兜底） */
        private boolean enabled = true;

        /** 最小长度 */
        private int minLength = 8;

        /** 最大长度 */
        private int maxLength = 100;

        /** 大写/小写/数字/特殊字符四类中至少包含的类别数（1-4） */
        private int minCategories = 3;

        /** 是否禁止密码包含用户名（忽略大小写） */
        private boolean forbidUsername = true;
    }
}
