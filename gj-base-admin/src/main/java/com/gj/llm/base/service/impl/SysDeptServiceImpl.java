package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.RoleDeptEntity;
import com.gj.llm.base.entity.SysDeptEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.mapper.RoleDeptMapper;
import com.gj.llm.base.mapper.SysDeptMapper;
import com.gj.llm.base.model.DeptCreateRequest;
import com.gj.llm.base.model.DeptUpdateRequest;
import com.gj.llm.base.service.SysDeptService;
import com.gj.llm.base.service.UserService;
import com.gj.llm.common.exception.WarnBusinessException;
import com.gj.llm.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部门服务实现 -- 树形结构管理（仿 MenuServiceImpl 的树构建惯例）。
 *
 * <p>{@code ancestors} 存祖级链路（如 {@code 0,100}）；换父时重算自身与全部子孙的链路；
 * 删除前校验无下级、无挂载用户，删除后清理 {@code sys_role_dept} 悬空引用。</p>
 *
 * <p>{@link UserService} 为双向域依赖（用户侧依赖部门树，部门删除校验依赖用户计数），
 * 用 {@code @Lazy} 代理打破构造期循环（显式构造器：{@code @RequiredArgsConstructor}
 * 不透传 {@code @Lazy} 到构造参数）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDeptEntity> implements SysDeptService {

    private final UserService userService;
    private final RoleDeptMapper roleDeptMapper;

    public SysDeptServiceImpl(@Lazy UserService userService, RoleDeptMapper roleDeptMapper) {
        this.userService = userService;
        this.roleDeptMapper = roleDeptMapper;
    }

    @Override
    public List<SysDeptEntity> tree() {
        return buildTree(list());
    }

    @Override
    @Transactional
    public SysDeptEntity create(DeptCreateRequest request) {
        Long parentId = request.getParentId();
        String ancestors = "0";
        if (parentId != null && parentId != 0L) {
            SysDeptEntity parent = getById(parentId);
            if (parent == null) {
                throw new WarnBusinessException("dept.parentNotFound", parentId);
            }
            ancestors = parent.getAncestors() + "," + parentId;
        }
        SysDeptEntity entity = SysDeptEntity.builder()
                .parentId(parentId == null ? 0L : parentId)
                .ancestors(ancestors)
                .name(request.getName())
                .sort(request.getSort() == null ? 0 : request.getSort())
                .leader(request.getLeader())
                .phone(request.getPhone())
                .email(request.getEmail())
                .build();
        save(entity);
        log.info("创建部门: {}（父级 {}）", entity.getName(), entity.getParentId());
        return entity;
    }

    @Override
    @Transactional
    public SysDeptEntity update(Long id, DeptUpdateRequest request) {
        SysDeptEntity entity = getById(id);
        if (entity == null) {
            throw new WarnBusinessException("dept.notFound", id);
        }
        entity.setName(request.getName());
        if (request.getSort() != null) {
            entity.setSort(request.getSort());
        }
        entity.setLeader(request.getLeader());
        entity.setPhone(request.getPhone());
        entity.setEmail(request.getEmail());
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }

        // 换父：新父须存在、不得移入自身子孙，重算自身与子孙的祖级链路
        if (request.getParentId() != null && !Objects.equals(entity.getParentId(), request.getParentId())) {
            changeParent(entity, request.getParentId());
        }
        updateById(entity);
        log.info("更新部门: {}", entity.getName());
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysDeptEntity entity = getById(id);
        if (entity == null) {
            throw new WarnBusinessException("dept.notFound", id);
        }
        long childCount = count(new LambdaQueryWrapper<SysDeptEntity>().eq(SysDeptEntity::getParentId, id));
        if (childCount > 0) {
            throw new WarnBusinessException("dept.hasChildren");
        }
        long userCount = userService.count(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getDeptId, id));
        if (userCount > 0) {
            throw new WarnBusinessException("dept.hasUsers", userCount);
        }
        removeById(id);
        // 清理角色自定义数据域中的悬空引用（sys_role_dept 无外键级联）
        roleDeptMapper.delete(new LambdaQueryWrapper<RoleDeptEntity>()
                .eq(RoleDeptEntity::getDeptId, id));
        log.info("删除部门: {}", entity.getName());
    }

    @Override
    public List<Long> listSubtreeIds(Long deptId) {
        List<Long> ids = new ArrayList<>();
        ids.add(deptId);
        // 全量表很小，内存遍历避免递归查询
        List<SysDeptEntity> all = list();
        collectChildren(deptId, all, ids);
        return ids;
    }

    // ==================== 内部方法 ====================

    /**
     * 换父校验与祖级链路重算。
     *
     * <p>链路重算：旧自路径 {@code oldPath = 旧ancestors + "," + 自身id}，
     * 新自路径 {@code newPath = 新ancestors + "," + 自身id}；
     * 子孙的 ancestors 以 oldPath 开头（含相等），统一替换前缀。</p>
     */
    private void changeParent(SysDeptEntity entity, Long newParentId) {
        String oldPath = entity.getAncestors() + "," + entity.getId();

        // 不得以自身为父：自环会让部门从树中消失、子孙链路重算与 collectChildren 无限递归
        if (Objects.equals(newParentId, entity.getId())) {
            throw new WarnBusinessException("dept.cyclicMoveSelf");
        }

        if (newParentId != 0L) {
            SysDeptEntity newParent = getById(newParentId);
            if (newParent == null) {
                throw new WarnBusinessException("dept.parentNotFound", newParentId);
            }
            // 不得移入自身或子孙：目标父的祖级链路中包含自身 ID
            if (containsId(newParent.getAncestors(), entity.getId())) {
                throw new WarnBusinessException("dept.cyclicMove");
            }
            entity.setAncestors(newParent.getAncestors() + "," + newParentId);
        } else {
            entity.setAncestors("0");
        }
        entity.setParentId(newParentId);

        String newPath = entity.getAncestors() + "," + entity.getId();
        List<SysDeptEntity> all = list();
        for (SysDeptEntity dept : all) {
            if (Objects.equals(dept.getId(), entity.getId())) {
                continue;
            }
            String path = dept.getAncestors();
            if (path == null) {
                continue;
            }
            // 直接子孙的 ancestors == oldPath；更深子孙以 oldPath + "," 开头
            if (path.equals(oldPath) || path.startsWith(oldPath + ",")) {
                dept.setAncestors(newPath + path.substring(oldPath.length()));
                updateById(dept);
            }
        }
        log.info("部门换父: id={}, 新父级={}, 新祖级链路={}", entity.getId(), newParentId, entity.getAncestors());
    }

    /** 判断祖级链路中是否包含指定 ID（严格按逗号分段匹配） */
    private boolean containsId(String ancestors, Long id) {
        if (StringUtils.isBlank(ancestors)) {
            return false;
        }
        for (String segment : ancestors.split(",")) {
            if (String.valueOf(id).equals(segment.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 内存遍历收集子部门 ID（visited 集防存量脏数据成环导致死循环） */
    private void collectChildren(Long parentId, List<SysDeptEntity> all, List<Long> ids) {
        collectChildren(parentId, all, ids, new HashSet<>());
    }

    private void collectChildren(Long parentId, List<SysDeptEntity> all, List<Long> ids, Set<Long> visited) {
        for (SysDeptEntity dept : all) {
            if (parentId.equals(dept.getParentId()) && visited.add(dept.getId())) {
                ids.add(dept.getId());
                collectChildren(dept.getId(), all, ids, visited);
            }
        }
    }

    /** 树构建（与 MenuServiceImpl.buildTree 同惯例） */
    private List<SysDeptEntity> buildTree(List<SysDeptEntity> depts) {
        if (depts == null || depts.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SysDeptEntity>> byParent = depts.stream()
                .collect(Collectors.groupingBy(d -> d.getParentId() == null ? 0L : d.getParentId()));
        depts.forEach(d -> {
            List<SysDeptEntity> children = byParent.getOrDefault(d.getId(), new ArrayList<>());
            children.sort(Comparator.comparingInt(c -> c.getSort() == null ? 0 : c.getSort()));
            d.setChildren(children);
        });
        return depts.stream()
                .filter(d -> d.getParentId() == null || d.getParentId() == 0L)
                .sorted(Comparator.comparingInt(d -> d.getSort() == null ? 0 : d.getSort()))
                .collect(Collectors.toList());
    }
}
