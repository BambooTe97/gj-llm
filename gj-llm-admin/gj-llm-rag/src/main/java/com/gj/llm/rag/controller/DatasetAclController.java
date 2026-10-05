package com.gj.llm.rag.controller;

import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.base.entity.ResourceAclEntity;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.service.ResourceAclService;
import com.gj.llm.base.service.RoleService;
import com.gj.llm.base.service.UserService;
import com.gj.llm.common.util.SecurityUtils;
import com.gj.llm.common.web.R;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.model.AclDetailVO;
import com.gj.llm.rag.model.AclGrantRequest;
import com.gj.llm.rag.model.AclGrantVO;
import com.gj.llm.rag.model.PrincipalOptionVO;
import com.gj.llm.rag.model.VisibilityUpdateRequest;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.rag.service.DatasetVisibleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库共享设置端点（库级 RBAC 管理面）—— 可见性切换 + 授权增删查 + 主体选择器。
 *
 * <p>读详情要求 {@code canAccessDataset}（可见者可读）；全部写操作要求
 * {@code canManageDataset}（仅 owner + 管理员），后端强校验，前端入口隐藏只是体验层。</p>
 */
@RestController
@RequestMapping("/api/v1/datasets/{datasetId}")
@RequiredArgsConstructor
public class DatasetAclController {

    private final DatasetService datasetService;
    private final DatasetVisibleService datasetVisibleService;
    private final ResourceAclService resourceAclService;
    private final UserService userService;
    private final RoleService roleService;

    /**
     * 共享设置详情：可见性 + 授权列表（主体展示名已解析）。
     */
    @GetMapping("/acl")
    public R<AclDetailVO> getAcl(@PathVariable Long datasetId) {
        DatasetEntity dataset = requireVisibleDataset(datasetId);
        List<AclGrantVO> grants = resourceAclService
                .listByResource(ResourceAclEntity.RESOURCE_TYPE_DATASET, datasetId)
                .stream()
                .map(acl -> AclGrantVO.builder()
                        .id(acl.getId())
                        .principalType(acl.getPrincipalType())
                        .principalId(acl.getPrincipalId())
                        .principalName(displayName(acl.getPrincipalType(), acl.getPrincipalId()))
                        .createdAt(acl.getCreatedAt())
                        .build())
                .toList();
        return R.ok(AclDetailVO.builder()
                .visibility(dataset.getVisibility())
                .grants(grants)
                .canManage(datasetVisibleService.canManageDataset(SecurityUtils.getCurrentUserId(), dataset))
                .build());
    }

    /**
     * 切换可见性（PUBLIC / RESTRICTED），仅 owner/管理员。
     */
    @PutMapping("/visibility")
    public R<Void> updateVisibility(@PathVariable Long datasetId, @Valid @RequestBody VisibilityUpdateRequest request) {
        requireManager(datasetId);
        datasetService.updateVisibility(datasetId, request.getVisibility());
        return R.ok(null, "可见性已更新");
    }

    /**
     * 新增授权（user/role 主体，存在性校验），仅 owner/管理员。
     */
    @PostMapping("/acl")
    public R<AclGrantVO> grant(@PathVariable Long datasetId, @Valid @RequestBody AclGrantRequest request) {
        requireManager(datasetId);
        String principalType = request.getPrincipalType();
        if (!AuthProperties.PRINCIPAL_USER.equals(principalType)
                && !AuthProperties.PRINCIPAL_ROLE.equals(principalType)) {
            throw new RuntimeException("非法主体类型: " + principalType + "（仅支持 user/role）");
        }
        if (AuthProperties.PRINCIPAL_USER.equals(principalType)
                && userService.getById(request.getPrincipalId()) == null) {
            throw new RuntimeException("用户不存在: id=" + request.getPrincipalId());
        }
        if (AuthProperties.PRINCIPAL_ROLE.equals(principalType)
                && roleService.getById(request.getPrincipalId()) == null) {
            throw new RuntimeException("角色不存在: id=" + request.getPrincipalId());
        }
        Long aclId = resourceAclService.grant(ResourceAclEntity.RESOURCE_TYPE_DATASET, datasetId,
                principalType, request.getPrincipalId(), SecurityUtils.getCurrentUserId());
        datasetVisibleService.invalidateAll();
        return R.ok(AclGrantVO.builder()
                .id(aclId)
                .principalType(principalType)
                .principalId(request.getPrincipalId())
                .principalName(displayName(principalType, request.getPrincipalId()))
                .build(), "授权成功");
    }

    /**
     * 移除授权（按 ACL 行 ID），仅 owner/管理员。
     */
    @DeleteMapping("/acl/{aclId}")
    public R<Void> revoke(@PathVariable Long datasetId, @PathVariable Long aclId) {
        requireManager(datasetId);
        if (!resourceAclService.revoke(aclId, ResourceAclEntity.RESOURCE_TYPE_DATASET, datasetId)) {
            throw new RuntimeException("授权记录不存在");
        }
        datasetVisibleService.invalidateAll();
        return R.ok(null, "已移除授权");
    }

    // ==================== 主体选择器 ====================

    /**
     * 按关键词搜索启用用户（共享面板下拉，仅返回 id + 展示名）。
     */
    @GetMapping("/acl/users")
    public R<List<PrincipalOptionVO>> searchUsers(@RequestParam(required = false) String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return R.ok(List.of());
        }
        List<PrincipalOptionVO> options = userService.page(1, 20, keyword.trim()).getRecords().stream()
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .map(u -> new PrincipalOptionVO(u.getId(), u.getNickname() + " (" + u.getUsername() + ")"))
                .toList();
        return R.ok(options);
    }

    /**
     * 角色列表（共享面板下拉）。
     */
    @GetMapping("/acl/roles")
    public R<List<PrincipalOptionVO>> listRoles() {
        return R.ok(roleService.listAll().stream()
                .map(r -> new PrincipalOptionVO(r.getId(), r.getName()))
                .toList());
    }

    // ==================== 内部 ====================

    private DatasetEntity requireVisibleDataset(Long datasetId) {
        DatasetEntity dataset = datasetService.getById(datasetId);
        if (dataset == null) {
            throw new RuntimeException("知识库不存在: id=" + datasetId);
        }
        if (!datasetVisibleService.canAccessDataset(SecurityUtils.getCurrentUserId(), datasetId)) {
            throw new RuntimeException("无权访问该知识库");
        }
        return dataset;
    }

    private void requireManager(Long datasetId) {
        DatasetEntity dataset = datasetService.getById(datasetId);
        if (dataset == null) {
            throw new RuntimeException("知识库不存在: id=" + datasetId);
        }
        if (!datasetVisibleService.canManageDataset(SecurityUtils.getCurrentUserId(), dataset)) {
            throw new RuntimeException("仅知识库所有者或管理员可管理共享设置");
        }
    }

    /** 主体展示名：用户取昵称兜底登录名，角色取名称。 */
    private String displayName(String principalType, Long principalId) {
        if (AuthProperties.PRINCIPAL_ROLE.equals(principalType)) {
            RoleEntity role = principalId == null ? null : roleService.getById(principalId);
            return role != null && role.getName() != null && !role.getName().isBlank()
                    ? role.getName() : "角色#" + principalId;
        }
        if (principalId == null) {
            return null;
        }
        UserEntity user = userService.getById(principalId);
        if (user == null) {
            return "用户#" + principalId;
        }
        return user.getNickname() != null && !user.getNickname().isBlank()
                ? user.getNickname() : user.getUsername();
    }
}
