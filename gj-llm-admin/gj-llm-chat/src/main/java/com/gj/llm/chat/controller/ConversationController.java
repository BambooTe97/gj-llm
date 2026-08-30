package com.gj.llm.chat.controller;

import com.gj.llm.chat.entity.MessageEntity;
import com.gj.llm.chat.model.ConversationCreateRequest;
import com.gj.llm.chat.model.ConversationVO;
import com.gj.llm.chat.model.MessageVO;
import com.gj.llm.chat.model.RenameRequest;
import com.gj.llm.chat.service.ConversationService;
import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.common.web.R;
import com.gj.llm.rag.service.Reference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话管理控制器 —— 会话 CRUD + 历史消息查询。
 *
 * @author gj-llm
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConversationService conversationService;

    /** 获取会话列表 */
    @GetMapping
    public R<List<ConversationVO>> list() {
        return R.ok(conversationService.listByUser());
    }

    /** 创建新会话 */
    @PostMapping
    public R<ConversationVO> create(@RequestBody ConversationCreateRequest request) {
        return R.ok(conversationService.create(request.getTitle(), request.getDatasetId()), "会话创建成功");
    }

    /** 重命名会话 */
    @PatchMapping("/{id}")
    public R<ConversationVO> rename(@PathVariable Long id, @RequestBody RenameRequest request) {
        return R.ok(conversationService.rename(id, request.getTitle()), "重命名成功");
    }

    /** 删除会话（逻辑删除） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        conversationService.remove(id);
        return R.ok(null, "删除成功");
    }

    /** 获取会话的历史消息 */
    @GetMapping("/{id}/messages")
    public R<List<MessageVO>> messages(@PathVariable Long id) {
        List<MessageEntity> messages = conversationService.getMessages(id);
        List<MessageVO> vos = messages.stream().map(m -> MessageVO.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .role(m.getRole())
                .content(m.getContent())
                .thinking(m.getThinking())
                .references(parseReferences(m.getMetadataJson()))
                .createdAt(m.getCreatedAt() != null ? m.getCreatedAt().format(DTF) : null)
                .build()).collect(Collectors.toList());
        return R.ok(vos);
    }

    /** 从 metadata_json 解析引用片段;格式异常或无引用时返回 null(历史消息降级为无角标展示) */
    private List<Reference> parseReferences(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            return JacksonUtils.listFromJson(metadataJson, "references", Reference.class);
        } catch (Exception e) {
            log.warn("解析消息引用元数据失败, 降级为无引用展示: {}", e.getMessage());
            return null;
        }
    }
}
