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

    /** Controller 简单类名 -> (HTTP 方法 -> 权限点 perms) */
    private static final Map<String, Map<String, String>> RULES = Map.of(
            "ChatController", Map.of("*", "chat:view"),
            "ConversationController", Map.of("*", "chat:view"),
            "DatasetController", Map.of(
                    "GET", "dataset:view",
                    "POST", "dataset:create",
                    "PUT", "dataset:edit",
                    "DELETE", "dataset:delete"),
            "UserController", Map.of(
                    "GET", "system:user:list",
                    "POST", "system:user:add",
                    "PUT", "system:user:edit",
                    "DELETE", "system:user:remove"),
            "RoleController", Map.of(
                    "GET", "system:role:list",
                    "POST", "system:role:add",
                    "PUT", "system:role:edit",
                    "DELETE", "system:role:remove"),
            "MenuController", Map.of(
                    "GET", "system:menu:list",
                    "POST", "system:menu:add",
                    "PUT", "system:menu:edit",
                    "DELETE", "system:menu:remove")
    );

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
            String perms = methodPerms.containsKey(httpMethod)
                    ? methodPerms.get(httpMethod)
                    : methodPerms.get("*");
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
