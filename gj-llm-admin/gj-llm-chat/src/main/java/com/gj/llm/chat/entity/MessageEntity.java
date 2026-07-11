package com.gj.llm.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对话消息实体 —— 映射 {@code chat_message} 表。
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class MessageEntity {

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的会话 ID（chat_conversation.id） */
    private Long conversationId;

    /** 角色：user / assistant / system */
    private String role;

    /** 消息内容 */
    private String content;

    /** 扩展元数据（JSON 格式：引用片段、token 数、模型信息等） */
    private String metadataJson;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
