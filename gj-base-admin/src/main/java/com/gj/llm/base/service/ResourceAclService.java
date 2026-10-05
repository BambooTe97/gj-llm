package com.gj.llm.base.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.ResourceAclEntity;

import java.util.List;

/**
 * 资源授权服务接口 —— resource_acl 表的幂等授权 / 回收 / 查询 / 级联清理。
 *
 * <p>只管 ACL 行本身；可见域组合（visibility/owner/grants）在业务模块
 * （gj-llm-rag 的 DatasetVisibleService）。每次写后失效授权缓存 ——
 * 角色主体授权的影响面不可知，按 {@code auth:grants:*} 模式全清。</p>
 *
 * <p><b>红线</b>：实现不触碰 ThreadLocal，操作者 userId 由调用方显式传入。</p>
 *
 * @author gj-llm
 */
public interface ResourceAclService extends IService<ResourceAclEntity> {

    /**
     * 授权（幂等）：同一 (资源, 主体) 重复授权不报错、不产生重复行。
     *
     * @return 本次的 ACL 行 ID（已存在则返回既有行 ID）
     */
    Long grant(String resourceType, Long resourceId, String principalType, Long principalId, Long createdBy);

    /**
     * 回收授权：按 ACL 行 ID 删除，并校验归属资源（防止跨资源误删）。
     *
     * @return 是否实际删除了行
     */
    boolean revoke(Long aclId, String resourceType, Long resourceId);

    /**
     * 资源的授权列表（按授权时间倒序）。
     */
    List<ResourceAclEntity> listByResource(String resourceType, Long resourceId);

    /**
     * 资源级联清理（删库时调用）。
     *
     * @return 删除的授权行数
     */
    long deleteByResource(String resourceType, Long resourceId);
}
