package com.gj.llm.rag.service;

import com.gj.llm.rag.entity.DatasetEntity;

import java.util.Set;

/**
 * 知识库可见域服务接口 —— 库级数据隔离的组合判定点（base-admin 提供原语，rag 负责组合）。
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
 * {@code DatasetServiceImpl} / ACL 管理端点按 {@link #VISIBLE_CACHE_PATTERN} 主动失效。</p>
 *
 * @author gj-llm
 */
public interface DatasetVisibleService {

    /** 可见集缓存键前缀 */
    String VISIBLE_CACHE_PREFIX = "rag:visible:u";

    /** 可见集缓存失效模式（授权写 / 库变更后全清） */
    String VISIBLE_CACHE_PATTERN = "rag:visible:*";

    /**
     * 用户可见的知识库 ID 集合（全量，不区分状态——就绪过滤由调用方做）。
     * userId 为 null 时返回空集（fail-closed，未认证用户无任何可见域）。
     */
    Set<Long> visibleDatasetIds(Long userId);

    /**
     * 是否可访问（查看/检索/聊天锁定该库）。库不存在返回 false。
     */
    boolean canAccessDataset(Long userId, Long datasetId);

    /**
     * 是否可管理（编辑/删除/上传/重建索引/共享设置）：仅 owner + 管理员。
     */
    boolean canManageDataset(Long userId, DatasetEntity dataset);

    /**
     * {@link #canManageDataset(Long, DatasetEntity)} 的便捷重载（按 ID 加载库）。
     */
    boolean canManageDataset(Long userId, Long datasetId);

    /**
     * 失效全部用户可见集缓存（授权写 / 共享设置变更后调用）。
     *
     * @return 删除的缓存键数量
     */
    long invalidateAll();
}
