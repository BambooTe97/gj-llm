package com.gj.llm.base.init;

import com.gj.llm.base.entity.ApiEntity;
import com.gj.llm.base.service.ApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 接口扫描器 -- 应用启动时遍历所有 Controller 的 {@code @RequestMapping}，
 * 通过 {@link ApiService} 将接口信息同步到 {@code sys_api} 表。
 *
 * <p><b>性能策略</b>：启动时一次性查出全部接口构建内存 Map（{@code controller#method -> ApiEntity}），
 * 扫描时只查内存、不逐条查库；最后批量 insert/update，避免 N 次数据库访问。</p>
 *
 * <p>以 controller 全限定类名 + 方法名为业务键 upsert：
 * <ul>
 *   <li>接口存在 -> 更新 path/httpMethod，并恢复 isDeleted=0（接口曾移除又恢复）</li>
 *   <li>接口新增 -> 插入</li>
 *   <li>本次未扫到的旧记录 -> 标记 isDeleted=1（保留历史，避免角色关联悬空）</li>
 * </ul>
 * 排除 {@code /open/**}（免登录）与 {@code /error}（Spring 内置）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(100)
public class ApiScanner implements ApplicationRunner {

    private final RequestMappingHandlerMapping handlerMapping;
    private final ApiService apiService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始扫描 Controller 接口...");
        Map<RequestMappingInfo, HandlerMethod> handlerMap = handlerMapping.getHandlerMethods();

        // 一次性查出全部接口，构建内存 Map（避免逐条查库）
        Map<String, ApiEntity> existingMap = apiService.list().stream()
                .collect(Collectors.toMap(
                        a -> a.getController() + "#" + a.getMethodName(),
                        a -> a,
                        (a, b) -> a));

        Set<String> scannedKeys = new HashSet<>();
        List<ApiEntity> toInsert = new ArrayList<>();
        List<ApiEntity> toUpdate = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMap.entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();

            String controller = handlerMethod.getBeanType().getName();
            String methodName = handlerMethod.getMethod().getName();

            PathPatternsRequestCondition ppc = info.getPathPatternsCondition();
            if (ppc == null) {
                continue;
            }
            Set<String> patterns = ppc.getPatternValues();
            if (patterns.isEmpty()) {
                continue;
            }
            String path = patterns.iterator().next();
            // 排除免登录前缀与内置错误页
            if (path.startsWith("/open") || path.startsWith("/error")) {
                continue;
            }

            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            String httpMethod = methods.isEmpty() ? "ALL" : methods.iterator().next().name();

            String simpleController = controller.substring(controller.lastIndexOf('.') + 1);
            log.info("扫描接口: {} {} -> {}.{}", httpMethod, path, simpleController, methodName);

            String key = controller + "#" + methodName;
            scannedKeys.add(key);

            ApiEntity existing = existingMap.get(key);
            if (existing != null) {
                int isDeleted = existing.getIsDeleted() == null ? 1 : existing.getIsDeleted();
                boolean changed = !Objects.equals(existing.getPath(), path)
                        || !Objects.equals(existing.getHttpMethod(), httpMethod)
                        || isDeleted != 0;
                if (changed) {
                    existing.setPath(path);
                    existing.setHttpMethod(httpMethod);
                    existing.setIsDeleted(0);
                    toUpdate.add(existing);
                }
            } else {
                toInsert.add(ApiEntity.builder()
                        .controller(controller)
                        .methodName(methodName)
                        .httpMethod(httpMethod)
                        .path(path)
                        .build());
            }
        }

        // 批量写入新增
        if (!toInsert.isEmpty()) {
            apiService.saveBatch(toInsert);
        }
        // 批量写入更新
        if (!toUpdate.isEmpty()) {
            apiService.updateBatchById(toUpdate);
        }
        // 标记本次未扫到的旧有效记录为已删除
        List<ApiEntity> toMarkDeleted = existingMap.values().stream()
                .filter(a -> a.getIsDeleted() == null || a.getIsDeleted() == 0)
                .filter(a -> !scannedKeys.contains(a.getController() + "#" + a.getMethodName()))
                .collect(Collectors.toList());
        toMarkDeleted.forEach(a -> a.setIsDeleted(1));
        if (!toMarkDeleted.isEmpty()) {
            apiService.updateBatchById(toMarkDeleted);
        }

        log.info("接口扫描完成: 新增={}, 更新={}, 标记删除={}, 当前有效={}",
                toInsert.size(), toUpdate.size(), toMarkDeleted.size(), scannedKeys.size());
    }
}
