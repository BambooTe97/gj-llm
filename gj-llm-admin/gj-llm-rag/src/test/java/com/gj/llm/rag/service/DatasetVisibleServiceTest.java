package com.gj.llm.rag.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.base.service.GrantService;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.redis.service.RedisService;
import com.gj.llm.rag.service.impl.DatasetVisibleServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DatasetVisibleService} 可见域判定矩阵(纯逻辑,mock 依赖,不触 DB/Redis)。
 *
 * <p>核心红线:<b>fail-closed</b> —— userId 为 null 恒无权限;RESTRICTED 库仅
 * owner/管理员/被授权主体可见;管理操作不因"被授权"而放开(v1 授权只读不管理)。</p>
 */
class DatasetVisibleServiceTest {

    @BeforeAll
    static void initEntityMetadata() {
        // LambdaQueryWrapper 依赖 MP 实体元数据缓存(正常由 Mapper 注册时初始化),纯单测手动注入
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), DatasetEntity.class);
    }

    private final DatasetService datasetService = mock(DatasetService.class);
    private final GrantService grantService = mock(GrantService.class);
    private final RedisService redisService = mock(RedisService.class);
    private final AuthProperties authProperties = new AuthProperties();

    private final DatasetVisibleService service =
            new DatasetVisibleServiceImpl(datasetService, grantService, redisService, authProperties);

    private DatasetEntity ds(Long id, String visibility, Long ownerId) {
        return DatasetEntity.builder().id(id).visibility(visibility).ownerId(ownerId).build();
    }

    // ==================== visibleDatasetIds ====================

    @Test
    void visibleDatasetIds_nullUser_failClosed() {
        assertThat(service.visibleDatasetIds(null)).isEmpty();
    }

    @Test
    void visibleDatasetIds_adminBypass_allDatasets() {
        when(grantService.isAdmin(9L)).thenReturn(true);
        when(datasetService.list()).thenReturn(List.of(
                ds(1L, "PUBLIC", 100L), ds(2L, "RESTRICTED", 100L)));
        assertThat(service.visibleDatasetIds(9L)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void visibleDatasetIds_normalUser_rowsPassedThrough_noExtraFiltering() {
        // mock 的 list() 即"PUBLIC OR owner OR id IN(grants)"查询的返回结果集;
        // 此处验证服务层对结果不再做二次过滤(老数据 NULL 视同 PUBLIC、RESTRICTED 但 owner=本人 都保留)
        when(grantService.isAdmin(7L)).thenReturn(false);
        DatasetEntity legacy = ds(1L, null, 100L);
        DatasetEntity own = ds(3L, "RESTRICTED", 7L);
        when(datasetService.list(any(Wrapper.class))).thenReturn(List.of(legacy, own));
        when(grantService.grantedResourceIds(7L, "dataset")).thenReturn(Set.of());
        assertThat(service.visibleDatasetIds(7L)).containsExactlyInAnyOrder(1L, 3L);
        verify(grantService).grantedResourceIds(7L, "dataset");
    }

    @Test
    void visibleDatasetIds_grantsConsulted_normalUserNotAdmin() {
        // 授权集合一定被征询(组合进 wrapper 的 IN 条件),管理员旁路不征询
        when(grantService.isAdmin(7L)).thenReturn(false);
        when(datasetService.list(any(Wrapper.class))).thenReturn(List.of(ds(4L, "RESTRICTED", 100L)));
        when(grantService.grantedResourceIds(7L, "dataset")).thenReturn(Set.of(4L));
        assertThat(service.visibleDatasetIds(7L)).containsExactly(4L);
    }

    @Test
    void visibleDatasetIds_admin_doesNotConsultGrants() {
        when(grantService.isAdmin(9L)).thenReturn(true);
        when(datasetService.list()).thenReturn(List.of(ds(1L, "RESTRICTED", 100L)));
        assertThat(service.visibleDatasetIds(9L)).containsExactly(1L);
        verify(grantService, never()).grantedResourceIds(anyLong(), anyString());
    }

    // ==================== canAccessDataset ====================

    @Test
    void canAccess_nullUser_failClosed() {
        assertThat(service.canAccessDataset(null, 1L)).isFalse();
    }

    @Test
    void canAccess_datasetMissing_failClosed() {
        assertThat(service.canAccessDataset(7L, 404L)).isFalse();
    }

    @Test
    void canAccess_publicVisibleToEveryone_includingLegacyNull() {
        when(datasetService.getById(2L)).thenReturn(ds(2L, "PUBLIC", 100L));
        when(datasetService.getById(1L)).thenReturn(ds(1L, null, 100L));
        assertThat(service.canAccessDataset(7L, 2L)).isTrue();
        assertThat(service.canAccessDataset(7L, 1L)).isTrue();
    }

    @Test
    void canAccess_restricted_ownerTrue_grantTrue_strangerFalse() {
        when(datasetService.getById(3L)).thenReturn(ds(3L, "RESTRICTED", 7L));
        // owner 恒可见,无需查角色/授权
        assertThat(service.canAccessDataset(7L, 3L)).isTrue();
        // 非 owner 走管理员判定,再走授权
        when(datasetService.getById(4L)).thenReturn(ds(4L, "RESTRICTED", 100L));
        when(grantService.isAdmin(7L)).thenReturn(false);
        when(grantService.hasGrant(7L, "dataset", 4L)).thenReturn(true);
        assertThat(service.canAccessDataset(7L, 4L)).isTrue();
        when(grantService.hasGrant(7L, "dataset", 4L)).thenReturn(false);
        assertThat(service.canAccessDataset(7L, 4L)).isFalse();
    }

    @Test
    void canAccess_restricted_adminBypass() {
        when(datasetService.getById(4L)).thenReturn(ds(4L, "RESTRICTED", 100L));
        when(grantService.isAdmin(9L)).thenReturn(true);
        assertThat(service.canAccessDataset(9L, 4L)).isTrue();
    }

    // ==================== canManageDataset ====================

    @Test
    void canManage_nullOrMissing_failClosed() {
        assertThat(service.canManageDataset(null, 1L)).isFalse();
        assertThat(service.canManageDataset(7L, 404L)).isFalse();
    }

    @Test
    void canManage_ownerTrue_adminTrue_granteeFalse() {
        // 授权主体(非 owner 非管理员)只读不管理 —— v1 管理边界
        when(datasetService.getById(4L)).thenReturn(ds(4L, "RESTRICTED", 100L));
        when(grantService.isAdmin(7L)).thenReturn(false);
        assertThat(service.canManageDataset(7L, 4L)).isFalse();

        when(grantService.isAdmin(9L)).thenReturn(true);
        assertThat(service.canManageDataset(9L, 4L)).isTrue();

        assertThat(service.canManageDataset(100L, ds(4L, "RESTRICTED", 100L))).isTrue();
    }
}
