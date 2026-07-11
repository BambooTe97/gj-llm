package com.gj.llm.chat.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发送消息请求 —— 流式 & 非流式共用。
 *
 * @author gj-llm
 */
@Data
public class ChatRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话 ID（必填，前端先创建会话再发消息） */
    private Long conversationId;

    /** 消息内容 */
    private String content;

    /** 关联的知识库 ID（可选，用于 RAG 检索） */
    private Long datasetId;

    /** 知识库类型标识（可选，兼容旧的 type 逻辑） */
    private String type;
}
