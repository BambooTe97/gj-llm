package com.gj.llm.chat.controller;

import com.gj.llm.chat.entity.MessageEntity;
import com.gj.llm.chat.model.ConversationCreateRequest;
import com.gj.llm.chat.model.ConversationVO;
import com.gj.llm.chat.model.MessageVO;
import com.gj.llm.chat.model.RenameRequest;
import com.gj.llm.chat.service.ConversationService;
import com.gj.llm.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话管理控制器 —— 会话 CRUD + 历史消息查询。
 *
 * @author gj-llm
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConversationService conversationService;

    /** 获取会话列表 */
    @GetMapping
    public ApiResponse<List<ConversationVO>> list() {
        return ApiResponse.ok(conversationService.listByUser());
    }

    /** 创建新会话 */
    @PostMapping
    public ApiResponse<ConversationVO> create(@RequestBody ConversationCreateRequest request) {
        return ApiResponse.ok(conversationService.create(request.getTitle(), request.getDatasetId()), "会话创建成功");
    }

    /** 重命名会话 */
    @PatchMapping("/{id}")
    public ApiResponse<ConversationVO> rename(@PathVariable Long id, @RequestBody RenameRequest request) {
        return ApiResponse.ok(conversationService.rename(id, request.getTitle()), "重命名成功");
    }

    /** 删除会话（逻辑删除） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        conversationService.remove(id);
        return ApiResponse.ok(null, "删除成功");
    }

    /** 获取会话的历史消息 */
    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageVO>> messages(@PathVariable Long id) {
        List<MessageEntity> messages = conversationService.getMessages(id);
        List<MessageVO> vos = messages.stream().map(m -> MessageVO.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .role(m.getRole())
                .content(m.getContent())
                .createdAt(m.getCreatedAt() != null ? m.getCreatedAt().format(DTF) : null)
                .build()).collect(Collectors.toList());
        return ApiResponse.ok(vos);
    }
}
