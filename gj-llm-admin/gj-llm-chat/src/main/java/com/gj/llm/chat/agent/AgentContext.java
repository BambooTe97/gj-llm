package com.gj.llm.chat.agent;

import com.gj.llm.chat.entity.ConversationEntity;
import com.gj.llm.chat.entity.MessageEntity;
import com.gj.llm.rag.model.RoutingDecision;
import com.gj.llm.rag.service.Reference;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.util.List;

/**
 * 智能体执行上下文 -- 编排器在路由前构建,贯穿智能体执行全程。
 *
 * <p>不可变部分(会话/用户消息/历史/数据集)由编排器注入;
 * 可变部分({@code fullAnswer}/{@code fullThinking}/{@code references}/{@code routingDecision})
 * 由智能体在执行过程中写入,编排器在流结束后读取,用于持久化与 done 事件。</p>
 *
 * <p>{@code routingDecision} 由路由器在 route 阶段写入(智能路由决策),
 * 检索型智能体读取它做多库检索;null 表示未经规划(显式锁库或旧路径)。</p>
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

    /** 显式指定的知识库 ID(request 直传,为 null 时由智能路由决策) */
    private final Long datasetId;

    /** 是否启用深度思考(用户开关) */
    private final boolean enableThinking;

    /** 最近的历史消息(按时间正序,user/assistant 交替) */
    private final List<MessageEntity> history;

    /** 发起提问的用户 ID(编排器在调用方线程解析后注入,经参数显式下传 —— 线程池内不触碰 ThreadLocal) */
    private final Long userId;

    /** 累积的完整答案(智能体流式写入,编排器读取做持久化) */
    private final StringBuffer fullAnswer = new StringBuffer();

    /** 累积的完整思考(智能体流式写入,编排器读取做持久化) */
    private final StringBuffer fullThinking = new StringBuffer();

    /** 检索命中的引用片段(智能体在 prepare 阶段写入,编排器读取随消息持久化) */
    private List<Reference> references = List.of();

    /** 智能路由决策(路由器在 route 阶段写入,检索型智能体读取) */
    private RoutingDecision routingDecision;

    /** 工具调用旁路事件通道(ToolCallAgent 专用,懒创建;tool_call/tool_result 与 LLM 流并行 merge) */
    private Sinks.Many<ServerSentEvent<String>> toolEventSink;

    public void setReferences(List<Reference> references) {
        this.references = references == null ? List.of() : references;
    }

    public void setRoutingDecision(RoutingDecision routingDecision) {
        this.routingDecision = routingDecision;
    }

    /** 工具事件通道(懒创建:sideChannel 组装期与工具执行线程都会访问) */
    public Sinks.Many<ServerSentEvent<String>> toolEventSink() {
        if (toolEventSink == null) {
            toolEventSink = Sinks.many().unicast().onBackpressureBuffer();
        }
        return toolEventSink;
    }

    /** LLM 流结束时闭合旁路通道,保证 merge 终止、done 事件可达(未用过则为空操作) */
    public void completeToolEvents() {
        if (toolEventSink != null) {
            toolEventSink.tryEmitComplete();
        }
    }
}
