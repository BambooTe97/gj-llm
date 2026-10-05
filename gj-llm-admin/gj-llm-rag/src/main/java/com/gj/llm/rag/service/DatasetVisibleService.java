package com.gj.llm.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.type.TypeReference;
import com.gj.llm.auth.config.AuthProperties;
import com.gj.llm.auth.entity.ResourceAclEntity;
import com.gj.llm.auth.service.GrantService;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库可见域服务 —— 库级数据隔离的组合判定点（auth 提供原语，rag 负责组合）。
 *
 * <p>判定规则（对齐 Dify/FastGPT 的库级 RBAC 工业默认）：</p>
 * <ul>
 *   <li>PUBLIC（含老数据 NULL）：全员可见</li>
 *   <li>RESTRICTED：仅 owner、管理员角色、显式授权主体（user/role）可见</li>
 *   <li>管理操作（改/删/传文件/改共享）：仅 owner + 管理员</li>
 * </ul>
 *
 * <p><b>红线</b>：</p>
 * <ul>
 *   <li>fail-closed：userId 为 null 或可见集为空 ⇒ 空结果/无权限，绝不无过滤放行</li>
 *   <li>不触碰 ThreadLocal，userId 由调用方（Controller / 聊天调用方线程）显式传入</li>
 * </ul>
 *
 * <p>缓存 {@code rag:visible:u{uid}}（TTL 兜底）；库增删改与授权写后由
 * {@link DatasetServiceImpl} / ACL 管理端点按 {@link #VISIBLE_CACHE_PATTERN} 主动失效。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetVisibleService {

    /** 可见集缓存键前缀 */
    public static final String VISIBLE_CACHE_PREFIX = "rag:visible:u";
    /** 可见集缓存失效模式（授权写 / 库变更后全清） */
    public static final String VISIBLE_CACHE_PATTERN = "rag:visible:*";

    private final DatasetService datasetService;
    private final GrantService grantService;
    private final RedisService redisService;
    private final AuthProperties authProperties;

    /**
     * 用户可见的知识库 ID 集合（全量，不区分状态——就绪过滤由调用方做）。
     * userId 为 null 时返回空集（fail-closed，未认证用户无任何可见域）。
     */
    public Set<Long> visibleDatasetIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        String key = VISIBLE_CACHE_PREFIX + userId;
        Set<Long> cached = redisService.get(key, new TypeReference<Set<Long>>() {
        });
        if (cached != null) {
            return cached;
        }
        Set<Long> visible = computeVisibleDatasetIds(userId);
        redisService.set(key, visible, Duration.ofSeconds(authProperties.getCacheTtlSeconds()));
        return visible;
    }

    private Set<Long> computeVisibleDatasetIds(Long userId) {
        // 管理员旁路：全库可见
        if (grantService.isAdmin(userId)) {
            return datasetService.list().stream().map(DatasetEntity::getId).collect(Collectors.toSet());
        }
        Set<Long> grants = grantService.grantedResourceIds(userId, ResourceAclEntity.RESOURCE_TYPE_DATASET);
        // PUBLIC（老数据 NULL 视同 PUBLIC）或本人创建或被授权
        LambdaQueryWrapper<DatasetEntity> wrapper = new LambdaQueryWrapper<DatasetEntity>()
                .select(DatasetEntity::getId)
                .and(v -> v.ne(DatasetEntity::getVisibility, AuthProperties.VISIBILITY_RESTRICTED)
                        .or().isNull(DatasetEntity::getVisibility))
                .or(v -> v.eq(DatasetEntity::getOwnerId, userId));
        if (!grants.isEmpty()) {
            wrapper.or(v -> v.in(DatasetEntity::getId, grants));
        }
        List<DatasetEntity> rows = datasetService.list(wrapper);
        return rows.stream().map(DatasetEntity::getId).collect(Collectors.toSet());
    }

    /**
     * 是否可访问（查看/检索/聊天锁定该库）。库不存在返回 false。
     */
    public boolean canAccessDataset(Long userId, Long datasetId) {
        if (userId == null || datasetId == null) {
            return false;
        }
        DatasetEntity dataset = datasetService.getById(datasetId);
        if (dataset == null) {
            return false;
        }
        // PUBLIC / 老数据 NULL：全员可见
        if (!AuthProperties.VISIBILITY_RESTRICTED.equals(dataset.getVisibility())) {
            return true;
        }
        return isRestrictedVisible(userId, dataset);
    }

    /**
     * 是否可管理（编辑/删除/上传/重建索引/共享设置）：仅 owner + 管理员。
     */
    public boolean canManageDataset(Long userId, DatasetEntity dataset) {
        if (userId == null || dataset == null) {
            return false;
        }
        return isRestrictedVisible(userId, dataset);
    }

    /**
     * {@link #canManageDataset(Long, DatasetEntity)} 的便捷重载（按 ID 加载库）。
     */
    public boolean canManageDataset(Long userId, Long datasetId) {
        if (userId == null || datasetId == null) {
            return false;
        }
        return canManageDataset(userId, datasetService.getById(datasetId));
    }

    /** RESTRICTED 库的可见/管理共同判定：owner → 管理员 → 显式授权 */
    private boolean isRestrictedVisible(Long userId, DatasetEntity dataset) {
        if (userId.equals(dataset.getOwnerId())) {
            return true;
        }
        if (grantService.isAdmin(userId)) {
            return true;
        }
        return grantService.hasGrant(userId, ResourceAclEntity.RESOURCE_TYPE_DATASET, dataset.getId());
    }

    /**
     * 失效全部用户可见集缓存（授权写 / 共享设置变更后调用）。
     *
     * @return 删除的缓存键数量
     */
    public long invalidateAll() {
        try {
            return redisService.deleteByPattern(VISIBLE_CACHE_PATTERN);
        } catch (Exception e) {
            log.warn("失效可见域缓存失败(TTL 兜底): {}", e.getMessage());
            return 0;
        }
    }
}
