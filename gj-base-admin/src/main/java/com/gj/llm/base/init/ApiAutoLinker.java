package com.gj.llm.base.init;

import com.gj.llm.base.entity.ApiEntity;
import com.gj.llm.base.entity.MenuApiEntity;
import com.gj.llm.base.entity.MenuEntity;
import com.gj.llm.base.service.ApiService;
import com.gj.llm.base.service.MenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 接口默认关联器 -- 在 {@link ApiScanner} 之后执行，按 Controller 简单类名 + HTTP 方法
 * 的约定规则，通过 {@link ApiService}、{@link MenuService} 为接口建立默认的
 * "权限点（菜单按钮）-接口"关联（{@code sys_menu_api}）。
 *
 * <p><b>性能策略</b>：启动时一次性加载接口、菜单（带 perms）、现有关联到内存，
 * 循环只查内存判断是否需要新增，最后批量插入，避免逐条查库。</p>
 *
 * <p>仅插入尚不存在的关联，不覆盖 admin 已手工调整的关联。约定规则：
 * <ul>
 *   <li>{@code ChatController}/{@code ConversationController} -> {@code chat:view}</li>
 *   <li>{@code DatasetController} -> GET=view / POST=create / PUT=edit / DELETE=delete</li>
 *   <li>{@code UserController} -> GET=list / POST=add / PUT=edit / DELETE=remove</li>
 *   <li>{@code RoleController} -> GET=list / POST=add / PUT=edit / DELETE=remove</li>
 *   <li>{@code MenuController} -> GET=list / POST=add / PUT=edit / DELETE=remove</li>
 * </ul>
 * 未匹配的 Controller（如 FileController）不自动关联，由 admin 在菜单管理页配置。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(200)
public class ApiAutoLinker implements ApplicationRunner {

    private final ApiService apiService;
    private final MenuService menuService;

    /** Controller 简单类名 -> (HTTP 方法 -> 权限点 perms)；条目超过 10 组，用 ofEntries */
    private static final Map<String, Map<String, String>> RULES = Map.ofEntries(
            Map.entry("ChatController", Map.of("*", "chat:view")),
            Map.entry("ConversationController", Map.of("*", "chat:view")),
            Map.entry("DatasetController", Map.of(
                    "GET", "dataset:view",
                    "POST", "dataset:create",
                    "PUT", "dataset:edit",
                    "DELETE", "dataset:delete")),
            Map.entry("McpApiKeyController", Map.of(
                    "GET", "mcp:key:list",
                    "POST", "mcp:key:create",
                    "PUT", "mcp:key:edit",
                    "DELETE", "mcp:key:remove")),
            Map.entry("McpServerConfigController", Map.of(
                    "GET", "mcp:server:list",
                    "POST", "mcp:server:create",
                    "PUT", "mcp:server:edit",
                    "DELETE", "mcp:server:remove")),
            Map.entry("McpAuditController", Map.of(
                    "GET", "mcp:audit:view")),
            Map.entry("NotifyController", Map.of("*", "notify:view")),
            Map.entry("NotifyAdminController", Map.of("*", "notify:manage")),
            Map.entry("UserController", Map.of(
                    "GET", "system:user:list",
                    "POST", "system:user:add",
                    "PUT", "system:user:edit",
                    "DELETE", "system:user:remove")),
            Map.entry("RoleController", Map.of(
                    "GET", "system:role:list",
                    "POST", "system:role:add",
                    "PUT", "system:role:edit",
                    "DELETE", "system:role:remove")),
            Map.entry("MenuController", Map.of(
                    "GET", "system:menu:list",
                    "POST", "system:menu:add",
                    "PUT", "system:menu:edit",
                    "DELETE", "system:menu:remove"))
    );

    /**
     * 精确路径规则 -- 优先于 {@link #RULES} 粗映射匹配。
     *
     * <p>用于把"页面纵深功能"从页面级 CRUD 大类中拆出独立权限点：路径模式为
     * AntPathMatcher 语义（{@code *}/{@code **} 通配，{var} 占位符段按普通文本匹配），
     * 需含 /api 前缀（ApiScanner 入库的是 Spring 完整模式值）。</p>
     */
    private record PathRule(String controller, String httpMethod, String pathPattern, String perms) {
    }

    private static final List<PathRule> PATH_RULES = List.of(
            // ---- 知识库详情页功能（权限点见 sql/gj-base/dataset-detail-perms.sql） ----
            new PathRule("DatasetController", "POST", "/api/v1/datasets/*/documents/upload", "dataset:doc:upload"),
            new PathRule("DatasetController", "DELETE", "/api/v1/datasets/*/documents/*", "dataset:doc:delete"),
            new PathRule("DatasetController", "POST", "/api/v1/datasets/*/documents/*/reparse", "dataset:doc:reparse"),
            new PathRule("DatasetController", "POST", "/api/v1/datasets/*/test", "dataset:test"),
            // 共享设置整体（可见性切换 / 授权增删 / 主体选择器）收敛到一个权限点
            new PathRule("DatasetController", "PUT", "/api/v1/datasets/*/visibility", "dataset:acl"),
            new PathRule("DatasetController", "POST", "/api/v1/datasets/*/acl", "dataset:acl"),
            new PathRule("DatasetController", "DELETE", "/api/v1/datasets/*/acl/*", "dataset:acl"),
            new PathRule("DatasetController", "GET", "/api/v1/datasets/*/acl/**", "dataset:acl")
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始建立接口默认关联...");

        // 一次性加载：接口、菜单(带 perms)、现有关联
        List<ApiEntity> apis = apiService.listActive();
        Map<String, Long> menuIdByPerms = menuService.listMenusWithPerms().stream()
                .collect(Collectors.toMap(MenuEntity::getPerms, MenuEntity::getId, (a, b) -> a));
        Set<String> existingLinks = menuService.listAllMenuApiLinks().stream()
                .map(link -> link.getMenuId() + "#" + link.getApiId())
                .collect(Collectors.toSet());

        List<MenuApiEntity> toLink = new ArrayList<>();
        for (ApiEntity api : apis) {
            String simpleName = simpleName(api.getController());
            Map<String, String> methodPerms = RULES.get(simpleName);
            if (methodPerms == null) {
                continue;
            }
            String httpMethod = api.getHttpMethod();

            // 先按精确路径规则匹配（内页功能权限点），未命中再退回方法粗映射
            String perms = null;
            for (PathRule rule : PATH_RULES) {
                if (rule.controller().equals(simpleName)
                        && rule.httpMethod().equals(httpMethod)
                        && pathMatcher.match(rule.pathPattern(), api.getPath())) {
                    perms = rule.perms();
                    break;
                }
            }
            if (perms == null) {
                perms = methodPerms.containsKey(httpMethod)
                        ? methodPerms.get(httpMethod)
                        : methodPerms.get("*");
            }
            if (perms == null) {
                continue;
            }

            Long menuId = menuIdByPerms.get(perms);
            if (menuId == null) {
                continue;
            }

            // 内存判断是否已存在关联，避免逐条查库
            String linkKey = menuId + "#" + api.getId();
            if (!existingLinks.contains(linkKey)) {
                toLink.add(new MenuApiEntity(menuId, api.getId()));
                log.info("关联接口: {} {} -> {}", api.getHttpMethod(), api.getPath(), perms);
            }
        }

        // 批量插入新关联
        if (!toLink.isEmpty()) {
            menuService.addApiLinks(toLink);
        }
        log.info("接口默认关联完成: 新增关联={}", toLink.size());
    }

    /** 取全限定类名的简单类名 */
    private String simpleName(String fullClassName) {
        int idx = fullClassName.lastIndexOf('.');
        return idx >= 0 ? fullClassName.substring(idx + 1) : fullClassName;
    }
}
