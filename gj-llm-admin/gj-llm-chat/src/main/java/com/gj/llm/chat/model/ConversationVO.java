package com.gj.llm.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话视图对象 —— 返回给前端的会话信息。
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO {

    private Long id;
    private String title;
    private Long datasetId;
    private String lastMessage;
    private Integer messageCount;
    private String createdAt;
    private String updatedAt;
}
