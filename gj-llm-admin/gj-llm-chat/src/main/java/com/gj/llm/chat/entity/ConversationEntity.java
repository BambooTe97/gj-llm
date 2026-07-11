package com.gj.llm.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对话会话实体 —— 映射 {@code chat_conversation} 表。
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_conversation")
public class ConversationEntity {

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话标题，默认"新对话" */
    @Builder.Default
    private String title = "新对话";

    /** 关联的知识库 ID（NULL 表示通用对话） */
    private Long datasetId;

    /** 创建用户 ID */
    private Long userId;

    /** 消息数量 */
    @Builder.Default
    private Integer messageCount = 0;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除：0=未删除，1=已删除 */
    @TableLogic
    @Builder.Default
    private Integer isDeleted = 0;
}
