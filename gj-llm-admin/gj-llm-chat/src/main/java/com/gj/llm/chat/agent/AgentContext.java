package com.gj.llm.chat.agent;

import com.gj.llm.chat.entity.ConversationEntity;
import com.gj.llm.chat.entity.MessageEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 智能体执行上下文 -- 编排器在路由前构建,贯穿智能体执行全程。
 *
 * <p>不可变部分(会话/用户消息/历史/数据集)由编排器注入;
 * 可变部分({@code fullAnswer}/{@code fullThinking})由智能体在流式过程中写入,
 * 编排器在流结束后读取,用于持久化与 done 事件。</p>
 *
 * @author gj-llm
 */
@Getter
@RequiredArgsConstructor
public class AgentContext {

    /** 当前会话 */
    private final ConversationEntity conversation;

    /** 用户本轮消息内容 */
    private final String userContent;

    /** 解析后的知识库 ID(request 优先,回退会话;为 null 表示通用对话) */
    private final Long datasetId;

    /** 是否启用深度思考(用户开关) */
    private final boolean enableThinking;

    /** 最近的历史消息(按时间正序,user/assistant 交替) */
    private final List<MessageEntity> history;

    /** 累积的完整答案(智能体流式写入,编排器读取做持久化) */
    private final StringBuffer fullAnswer = new StringBuffer();

    /** 累积的完整思考(智能体流式写入,编排器读取做持久化) */
    private final StringBuffer fullThinking = new StringBuffer();
}
