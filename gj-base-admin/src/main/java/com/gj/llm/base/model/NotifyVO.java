package com.gj.llm.base.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知视图对象 —— 列表 / 推送 payload 共用的对外结构。
 *
 * <p>id 保持 Long：REST 与 WS 共用同一份 JacksonUtils mapper（全局 Long→String），
 * 出口自动为字符串，前端以字符串承载规避雪花 ID 精度问题。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyVO {

    private Long id;

    private String title;

    private String content;

    /** 级别：info / success / warning / error */
    private String level;

    /** 是否已读：0=未读，1=已读 */
    private Integer readFlag;

    /** 创建时间（yyyy-MM-dd HH:mm:ss） */
    private String createdAt;
}
