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
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 接口权限缓存 -- 启动时（在 ApiScanner/ApiAutoLinker 之后）通过 {@link ApiService}、
 * {@link MenuService} 加载接口与权限点映射，供 {@code ApiPermissionInterceptor} 请求时查询。
 *
 * <p>缓存两部分数据：
 * <ul>
 *   <li>{@code apiEntries}：有效接口列表（HTTP 方法 + PathPattern + apiId），用于匹配请求</li>
 *   <li>{@code apiPermsMap}：apiId -> 权限点 perms 集合（接口关联的菜单按钮权限点）</li>
 * </ul>
 * 监听 {@link ApiPermissionChangedEvent} 自动刷新；不直接依赖任何 Mapper。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(300)
public class ApiPermissionCache implements ApplicationRunner {

    private final ApiService apiService;
    private final MenuService menuService;

    private final PathPatternParser patternParser = new PathPatternParser();

    /** 有效接口列表（请求匹配用） */
    private volatile List<ApiEntry> apiEntries = List.of();

    /** apiId -> 权限点 perms 集合（接口校验用） */
    private volatile Map<Long, Set<String>> apiPermsMap = Map.of();

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    /** 监听权限变更事件，自动刷新缓存 */
    @EventListener
    public void onPermissionChanged(ApiPermissionChangedEvent event) {
        refresh();
    }

    /**
     * 重建缓存。启动时与权限配置变更时调用。
     */
    public synchronized void refresh() {
        log.info("刷新接口权限缓存...");

        // 通过 ApiService 获取有效接口
        List<ApiEntity> apis = apiService.listActive();

        // 解析每个接口路径为 PathPattern
        List<ApiEntry> entries = new ArrayList<>();
        for (ApiEntity api : apis) {
            try {
                PathPattern pattern = patternParser.parse(api.getPath());
                entries.add(new ApiEntry(api.getHttpMethod(), pattern, api.getId()));
            } catch (Exception e) {
                log.warn("解析接口路径失败: {}", api.getPath(), e);
            }
        }

        // 通过 MenuService 获取 apiId -> menuIds（接口关联的菜单按钮）
        List<MenuApiEntity> allLinks = menuService.listAllMenuApiLinks();
        Map<Long, List<Long>> apiToMenuIds = allLinks.stream()
                .collect(Collectors.groupingBy(MenuApiEntity::getApiId,
                        Collectors.mapping(MenuApiEntity::getMenuId, Collectors.toList())));

        // 通过 MenuService 获取 menuId -> perms
        List<MenuEntity> allMenus = menuService.listMenusWithPerms();
        Map<Long, String> menuPerms = allMenus.stream()
                .collect(Collectors.toMap(MenuEntity::getId, MenuEntity::getPerms, (a, b) -> a));

        // apiId -> perms 集合
        Map<Long, Set<String>> permsMap = new HashMap<>();
        for (ApiEntity api : apis) {
            List<Long> menuIds = apiToMenuIds.getOrDefault(api.getId(), List.of());
            Set<String> perms = menuIds.stream()
                    .map(menuPerms::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!perms.isEmpty()) {
                permsMap.put(api.getId(), perms);
            }
        }

        this.apiEntries = entries;
        this.apiPermsMap = permsMap;
        log.info("接口权限缓存刷新完成: 有效接口={}, 已配置权限接口={}", entries.size(), permsMap.size());
    }

    /**
     * 按 HTTP 方法 + 路径匹配接口，返回 apiId（无匹配返回 null）。
     */
    public Long matchApi(String httpMethod, String path) {
        PathContainer pathContainer = PathContainer.parsePath(path);
        for (ApiEntry entry : apiEntries) {
            if (matchesMethod(entry.method(), httpMethod)
                    && entry.pathPattern().matches(pathContainer)) {
                return entry.apiId();
            }
        }
        return null;
    }

    /**
     * 获取接口关联的权限点集合（空集合表示接口未配置权限点）。
     */
    public Set<String> getPerms(Long apiId) {
        return apiPermsMap.getOrDefault(apiId, Set.of());
    }

    private boolean matchesMethod(String apiMethod, String requestMethod) {
        return "ALL".equals(apiMethod) || apiMethod.equalsIgnoreCase(requestMethod);
    }

    /** 接口缓存条目 */
    private record ApiEntry(String method, PathPattern pathPattern, Long apiId) {
    }
}
