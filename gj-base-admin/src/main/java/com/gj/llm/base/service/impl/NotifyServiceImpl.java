package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.common.exception.WarnBusinessException;
import com.gj.llm.base.entity.NotifyEntity;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.mapper.NotifyMapper;
import com.gj.llm.base.model.NotifyAdminVO;
import com.gj.llm.base.model.NotifyVO;
import com.gj.llm.base.service.NotifyService;
import com.gj.llm.base.service.UserService;
import com.gj.llm.common.util.SecurityUtils;
import com.gj.llm.common.util.StringUtils;
import com.gj.llm.netty.protocol.MessageEnvelope;
import com.gj.llm.netty.service.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知服务实现 —— 先落库后推送；推送门面经 {@link ObjectProvider} 注入，
 * gj-netty 未启用（默认关闭）时功能照常工作，仅失去实时提醒能力。
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyServiceImpl extends ServiceImpl<NotifyMapper, NotifyEntity> implements NotifyService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Set<String> VALID_LEVELS = Set.of("info", "success", "warning", "error");

    /** 推送门面（基座启用时才有实例；缺省为 null，通知仍落库） */
    private final ObjectProvider<PushService> pushServiceProvider;

    /** RBAC 用户服务（接收人存在性校验 + 用户名批量解析） */
    private final UserService userService;

    // ==================== 查询 ====================

    @Override
    public List<NotifyVO> listByUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return List.of();
        }
        List<NotifyEntity> entities = list(new LambdaQueryWrapper<NotifyEntity>()
                .eq(NotifyEntity::getUserId, userId)
                .orderByDesc(NotifyEntity::getCreatedAt)
                .last("LIMIT 50"));
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public long unreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return 0;
        }
        return count(new LambdaQueryWrapper<NotifyEntity>()
                .eq(NotifyEntity::getUserId, userId)
                .eq(NotifyEntity::getReadFlag, 0));
    }

    // ==================== 已读管理 ====================

    @Override
    public boolean markRead(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return update(new LambdaUpdateWrapper<NotifyEntity>()
                .eq(NotifyEntity::getId, id)
                .eq(NotifyEntity::getUserId, userId)
                .eq(NotifyEntity::getReadFlag, 0)
                .set(NotifyEntity::getReadFlag, 1)
                .set(NotifyEntity::getReadAt, LocalDateTime.now()));
    }

    @Override
    public int markAllRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return 0;
        }
        return baseMapper.update(null, new LambdaUpdateWrapper<NotifyEntity>()
                .eq(NotifyEntity::getUserId, userId)
                .eq(NotifyEntity::getReadFlag, 0)
                .set(NotifyEntity::getReadFlag, 1)
                .set(NotifyEntity::getReadAt, LocalDateTime.now()));
    }

    @Override
    public int markReadByPrincipal(String principalId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        long userId;
        try {
            userId = Long.parseLong(principalId);
        } catch (NumberFormatException e) {
            log.warn("WS 已读回执的 principalId 非数字，忽略: {}", principalId);
            return 0;
        }
        return baseMapper.update(null, new LambdaUpdateWrapper<NotifyEntity>()
                .in(NotifyEntity::getId, ids)
                .eq(NotifyEntity::getUserId, userId)
                .eq(NotifyEntity::getReadFlag, 0)
                .set(NotifyEntity::getReadFlag, 1)
                .set(NotifyEntity::getReadAt, LocalDateTime.now()));
    }

    // ==================== 创建与推送 ====================

    @Override
    public NotifyVO createAndPush(Long targetUserId, String title, String content, String level) {
        if (targetUserId == null || userService.getById(targetUserId) == null) {
            throw new WarnBusinessException("notify.receiverNotFound", targetUserId);
        }
        String safeLevel = VALID_LEVELS.contains(level) ? level : "info";

        // 1) 先落库 —— 事实源，离线可补齐
        NotifyEntity entity = NotifyEntity.builder()
                .userId(targetUserId)
                .title(title)
                .content(content)
                .level(safeLevel)
                .createdAt(LocalDateTime.now())
                .build();
        save(entity);

        // 2) 后推送 —— 仅实时提醒，失败/离线不影响结果（库里有）
        NotifyVO vo = toVO(entity);
        PushService pushService = pushServiceProvider.getIfAvailable();
        if (pushService == null) {
            log.info("gj-netty 未启用，通知仅落库: userId={}, title={}", targetUserId, title);
            return vo;
        }
        boolean delivered = pushService.send(String.valueOf(targetUserId),
                MessageEnvelope.of(NotifyService.TOPIC_MESSAGE, vo));
        log.info("通知推送完成: userId={}, delivered={}, id={}", targetUserId, delivered, entity.getId());
        return vo;
    }

    // ==================== 管理端（通知管理中心） ====================

    @Override
    public IPage<NotifyAdminVO> pageForAdmin(long page, long size, String keyword, String level, Integer readFlag) {
        IPage<NotifyEntity> result = page(new Page<>(page, size), new LambdaQueryWrapper<NotifyEntity>()
                .and(StringUtils.isNotBlank(keyword), w -> w
                        .like(NotifyEntity::getTitle, keyword)
                        .or()
                        .like(NotifyEntity::getContent, keyword))
                .eq(StringUtils.isNotBlank(level), NotifyEntity::getLevel, level)
                .eq(readFlag != null, NotifyEntity::getReadFlag, readFlag)
                .orderByDesc(NotifyEntity::getCreatedAt));

        // 批量解析接收人用户名（复用 RBAC 用户服务，免 N+1 查询）
        Map<Long, UserEntity> users = result.getRecords().isEmpty() ? Map.of()
                : userService.listByIds(result.getRecords().stream()
                        .map(NotifyEntity::getUserId).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(UserEntity::getId, u -> u, (a, b) -> a));

        return result.convert(entity -> toAdminVO(entity, users));
    }

    // ==================== 私有辅助 ====================

    private NotifyVO toVO(NotifyEntity entity) {
        return NotifyVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .level(entity.getLevel())
                .readFlag(entity.getReadFlag())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().format(DTF) : null)
                .build();
    }

    private NotifyAdminVO toAdminVO(NotifyEntity entity, Map<Long, UserEntity> users) {
        UserEntity user = users.get(entity.getUserId());
        return NotifyAdminVO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .username(user != null ? user.getUsername() : String.valueOf(entity.getUserId()))
                .nickname(user != null ? user.getNickname() : null)
                .title(entity.getTitle())
                .content(entity.getContent())
                .level(entity.getLevel())
                .readFlag(entity.getReadFlag())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().format(DTF) : null)
                .readAt(entity.getReadAt() != null ? entity.getReadAt().format(DTF) : null)
                .build();
    }
}
