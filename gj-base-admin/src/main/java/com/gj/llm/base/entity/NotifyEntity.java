package com.gj.llm.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通知消息实体 —— 映射 {@code notify_message} 表。
 *
 * <p>通知以落库为事实源，WebSocket 推送只是实时提醒：
 * 用户离线时消息仍在库中，上线后经 REST 拉取补齐。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("notify_message")
public class NotifyEntity {

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 接收用户 ID */
    private Long userId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 级别：info / success / warning / error（对应前端通知样式） */
    @Builder.Default
    private String level = "info";

    /** 是否已读：0=未读，1=已读 */
    @Builder.Default
    private Integer readFlag = 0;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 阅读时间（未读为 NULL） */
    private LocalDateTime readAt;
}
