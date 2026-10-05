package com.gj.llm.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库授权项 VO —— 共享面板展示（主体展示名已解析）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclGrantVO {

    /** ACL 行 ID（移除授权时使用） */
    private Long id;

    /** 主体类型：user | role */
    private String principalType;

    /** 主体 ID */
    private Long principalId;

    /** 主体展示名（用户昵称/登录名、角色名称） */
    private String principalName;

    /** 授权时间 */
    private LocalDateTime createdAt;
}
