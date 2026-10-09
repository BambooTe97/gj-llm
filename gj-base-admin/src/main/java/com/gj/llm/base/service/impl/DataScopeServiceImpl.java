package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gj.llm.base.config.AuthProperties;
import com.gj.llm.base.entity.RoleDeptEntity;
import com.gj.llm.base.entity.RoleEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.mapper.RoleDeptMapper;
import com.gj.llm.base.model.DataScope;
import com.gj.llm.base.service.DataScopeService;
import com.gj.llm.base.service.RoleService;
import com.gj.llm.base.service.SysDeptService;
import com.gj.llm.base.service.UserService;
import com.gj.llm.common.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据权限服务实现 -- 解析角色数据域五档并聚合为 {@link DataScope}。
 *
 * <h3>解析规则</h3>
 * <ol>
 *   <li>当前用户 ID 为空（未认证）→ 空域（fail-closed）</li>
 *   <li>任一角色编码命中 {@code app.auth.admin-roles} → 全域</li>
 *   <li>多角色取最宽数据域（min(dataScope)）：1=全部 → 全域</li>
 *   <li>2=自定义 → 该角色在 {@code sys_role_dept} 的部门并集</li>
 *   <li>3=本部门 / 4=本部门及以下 → 基于当前用户 deptId（为 null 则该角色不贡献）</li>
 *   <li>5=仅本人 → selfUserIds</li>
 * </ol>
 *
 * <p>{@link UserService} 以 {@code @Lazy} 注入断环：UserServiceImpl（applyUserScope 消费方）
 * 依赖本服务，本服务聚合时又要经 UserService 查用户/角色关联。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class DataScopeServiceImpl implements DataScopeService {

    private final RoleService roleService;
    private final SysDeptService sysDeptService;
    private final RoleDeptMapper roleDeptMapper;
    private final AuthProperties authProperties;
    private final UserService userService;

    public DataScopeServiceImpl(RoleService roleService, SysDeptService sysDeptService,
                                RoleDeptMapper roleDeptMapper, AuthProperties authProperties,
                                @Lazy UserService userService) {
        this.roleService = roleService;
        this.sysDeptService = sysDeptService;
        this.roleDeptMapper = roleDeptMapper;
        this.authProperties = authProperties;
        this.userService = userService;
    }

    @Override
    public DataScope resolve() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return DataScope.EMPTY;
        }
        List<Long> roleIds = userService.getRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return DataScope.EMPTY;
        }
        List<RoleEntity> roles = roleService.listByIds(roleIds);
        if (roles.isEmpty()) {
            return DataScope.EMPTY;
        }

        // 管理员角色绕过（编码不区分大小写比对）
        Set<String> codes = roles.stream()
                .map(RoleEntity::getCode)
                .map(c -> c == null ? "" : c.toLowerCase())
                .collect(Collectors.toSet());
        for (String adminRole : authProperties.getAdminRoles()) {
            if (codes.contains(adminRole.toLowerCase())) {
                return DataScope.ALL;
            }
        }

        // 多角色取最宽数据域：任一角色为"全部"即全域
        int minScope = roles.stream()
                .mapToInt(DataScopeServiceImpl::scopeOf)
                .min()
                .orElse(RoleEntity.DATA_SCOPE_ALL);
        if (minScope == RoleEntity.DATA_SCOPE_ALL) {
            return DataScope.ALL;
        }

        Set<Long> deptIds = new HashSet<>();
        Set<Long> selfUserIds = new HashSet<>();
        // 自定义档：所有 dataScope=2 角色的部门并集
        for (RoleEntity role : roles) {
            if (scopeOf(role) == RoleEntity.DATA_SCOPE_CUSTOM) {
                deptIds.addAll(roleDeptMapper.selectDeptIdsByRoleId(role.getId()));
            }
        }
        // 本部门/本部门及以下：基于当前用户 deptId；仅本人：当前用户 ID
        Long deptId = null;
        UserEntity user = userService.getById(userId);
        if (user != null) {
            deptId = user.getDeptId();
        }
        for (RoleEntity role : roles) {
            int ds = scopeOf(role);
            if (ds == RoleEntity.DATA_SCOPE_DEPT && deptId != null) {
                deptIds.add(deptId);
            } else if (ds == RoleEntity.DATA_SCOPE_DEPT_AND_CHILD && deptId != null) {
                deptIds.addAll(sysDeptService.listSubtreeIds(deptId));
            } else if (ds == RoleEntity.DATA_SCOPE_SELF) {
                selfUserIds.add(userId);
            }
            // 3/4 档但用户无部门 → 该角色不贡献（不报错，fail-closed 收窄）
        }
        return new DataScope(false, Set.copyOf(deptIds), Set.copyOf(selfUserIds));
    }

    @Override
    public void applyUserScope(LambdaQueryWrapper<UserEntity> wrapper) {
        DataScope scope = resolve();
        if (scope.all()) {
            return;
        }
        applyScopeCondition(wrapper, scope.deptIds(), scope.selfUserIds(), scope.isEmpty());
    }

    @Override
    public List<Long> resolveScopedUserIds() {
        DataScope scope = resolve();
        // null = 全域不过滤（调用方语义：不追加 user_id 条件）
        if (scope.all()) {
            return null;
        }
        if (scope.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .select(UserEntity::getId);
        applyScopeCondition(wrapper, scope.deptIds(), scope.selfUserIds(), false);
        return userService.list(wrapper).stream().map(UserEntity::getId).toList();
    }

    /** deptIds/selfUserIds 组合条件（共用三分支：并存 OR 组合 / 仅部门 / 仅本人） */
    private void applyScopeCondition(LambdaQueryWrapper<UserEntity> wrapper,
                                     Set<Long> deptIds, Set<Long> selfUserIds, boolean empty) {
        boolean hasDept = deptIds != null && !deptIds.isEmpty();
        boolean hasSelf = selfUserIds != null && !selfUserIds.isEmpty();
        if (empty || (!hasDept && !hasSelf)) {
            wrapper.apply("1=0");
        } else if (hasDept && hasSelf) {
            wrapper.and(w -> w.in(UserEntity::getDeptId, deptIds)
                    .or().in(UserEntity::getId, selfUserIds));
        } else if (hasDept) {
            wrapper.in(UserEntity::getDeptId, deptIds);
        } else {
            wrapper.in(UserEntity::getId, selfUserIds);
        }
    }

    /** 角色数据域取值（null 宽容为"全部"，与 DB 默认值一致） */
    private static int scopeOf(RoleEntity role) {
        return role.getDataScope() == null ? RoleEntity.DATA_SCOPE_ALL : role.getDataScope();
    }
}
