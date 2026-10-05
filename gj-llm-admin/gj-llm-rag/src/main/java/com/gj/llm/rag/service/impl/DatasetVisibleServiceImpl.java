package com.gj.llm.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.base.entity.ResourceAclEntity;
import com.gj.llm.base.service.GrantService;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.rag.service.DatasetVisibleService;
import com.gj.llm.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库可见域服务实现 —— PUBLIC/RESTRICTED + owner/管理员/授权的组合判定，
 * 可见集 Redis 缓存（TTL 兜底 + 主动失效）。
 *
 * <p><b>红线</b>：fail-closed；不触碰 ThreadLocal，userId 由调用方显式传入。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetVisibleServiceImpl implements DatasetVisibleService {

    private final DatasetService datasetService;
    private final GrantService grantService;
    private final RedisService redisService;
    private final AuthProperties authProperties;

    @Override
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

    @Override
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

    @Override
    public boolean canManageDataset(Long userId, DatasetEntity dataset) {
        if (userId == null || dataset == null) {
            return false;
        }
        return isRestrictedVisible(userId, dataset);
    }

    @Override
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

    @Override
    public long invalidateAll() {
        try {
            return redisService.deleteByPattern(VISIBLE_CACHE_PATTERN);
        } catch (Exception e) {
            log.warn("失效可见域缓存失败(TTL 兜底): {}", e.getMessage());
            return 0;
        }
    }
}
