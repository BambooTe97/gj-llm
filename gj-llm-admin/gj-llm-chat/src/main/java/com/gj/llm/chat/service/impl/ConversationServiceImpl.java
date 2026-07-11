package com.gj.llm.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.chat.entity.ConversationEntity;
import com.gj.llm.chat.entity.MessageEntity;
import com.gj.llm.chat.mapper.ConversationMapper;
import com.gj.llm.chat.mapper.MessageMapper;
import com.gj.llm.chat.model.ConversationVO;
import com.gj.llm.chat.service.ConversationService;
import com.gj.llm.common.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话服务实现。
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, ConversationEntity> implements ConversationService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MessageMapper messageMapper;

    public ConversationServiceImpl(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public List<ConversationVO> listByUser() {
        String username = SecurityUtils.getCurrentUsername();
        // 简单用用户名匹配（后续可优化为 userId 查询）
        List<ConversationEntity> entities = list(
                new LambdaQueryWrapper<ConversationEntity>()
                        .orderByDesc(ConversationEntity::getUpdatedAt));

        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConversationVO create(String title, Long datasetId) {
        ConversationEntity entity = ConversationEntity.builder()
                .title(title != null && !title.isBlank() ? title : "新对话")
                .datasetId(datasetId)
                .userId(getCurrentUserId())
                .build();
        save(entity);
        log.info("创建会话成功: id={}, title={}", entity.getId(), entity.getTitle());
        return toVO(entity);
    }

    @Override
    @Transactional
    public ConversationVO rename(Long id, String title) {
        ConversationEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("会话不存在: id=" + id);
        }
        entity.setTitle(title);
        updateById(entity);
        log.info("重命名会话成功: id={}, title={}", id, title);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void remove(Long id) {
        ConversationEntity entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("会话不存在: id=" + id);
        }
        removeById(id); // MyBatis-Plus @TableLogic 自动执行逻辑删除
        log.info("删除会话成功: id={}", id);
    }

    @Override
    public List<MessageEntity> getMessages(Long conversationId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<MessageEntity>()
                        .eq(MessageEntity::getConversationId, conversationId)
                        .orderByAsc(MessageEntity::getCreatedAt));
    }

    // ==================== 私有方法 ====================

    private ConversationVO toVO(ConversationEntity entity) {
        if (entity == null) return null;
        return ConversationVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .datasetId(entity.getDatasetId())
                .messageCount(entity.getMessageCount())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().format(DTF) : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().format(DTF) : null)
                .build();
    }

    /** 获取当前用户 ID（从 SecurityContext 中解析） */
    private Long getCurrentUserId() {
        // 使用用户名 hash 作为简单 userId（后续可改为从 UserDetails 中获取真实 ID）
        String username = SecurityUtils.getCurrentUsername();
        return (long) Math.abs(username.hashCode());
    }
}
