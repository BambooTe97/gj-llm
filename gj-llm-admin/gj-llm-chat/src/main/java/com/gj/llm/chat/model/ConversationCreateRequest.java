package com.gj.llm.chat.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建会话请求。
 *
 * @author gj-llm
 */
@Data
public class ConversationCreateRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话标题（可选，默认"新对话"） */
    private String title;

    /** 关联的知识库 ID（可选） */
    private Long datasetId;
}
