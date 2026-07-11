package com.gj.llm.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.chat.entity.ConversationEntity;
import com.gj.llm.chat.entity.MessageEntity;
import com.gj.llm.chat.model.ConversationVO;

import java.util.List;

/**
 * 会话服务接口。
 *
 * @author gj-llm
 */
public interface ConversationService extends IService<ConversationEntity> {

    /** 获取当前用户的会话列表（按更新时间倒序） */
    List<ConversationVO> listByUser();

    /** 创建新会话 */
    ConversationVO create(String title, Long datasetId);

    /** 重命名会话 */
    ConversationVO rename(Long id, String title);

    /** 删除会话（逻辑删除） */
    void remove(Long id);

    /** 获取会话的历史消息 */
    List<MessageEntity> getMessages(Long conversationId);
}
