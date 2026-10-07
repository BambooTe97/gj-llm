package com.gj.llm.base.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知管理视图对象 —— 通知管理中心分页列表用，附接收人用户名（免前端二次联查）。
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyAdminVO {

    private Long id;

    /** 接收用户 ID */
    private Long userId;

    /** 接收用户名（服务端批量解析） */
    private String username;

    /** 用户昵称（可空） */
    private String nickname;

    private String title;

    private String content;

    /** 级别：info / success / warning / error */
    private String level;

    /** 是否已读：0=未读，1=已读 */
    private Integer readFlag;

    /** 创建时间（yyyy-MM-dd HH:mm:ss） */
    private String createdAt;

    /** 阅读时间（未读为 NULL） */
    private String readAt;
}
