package com.gj.llm.chat.model;

import com.gj.llm.rag.service.Reference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 消息视图对象 -- 返回给前端的消息信息。
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {

    private Long id;
    private Long conversationId;
    private String role;
    private String content;

    /** 模型思考内容(仅 assistant 有值,前端折叠展示) */
    private String thinking;

    /** RAG 检索引用片段(仅 assistant 有值,用于还原行内角标 [n] 与参考来源面板) */
    private List<Reference> references;

    private String createdAt;
}
