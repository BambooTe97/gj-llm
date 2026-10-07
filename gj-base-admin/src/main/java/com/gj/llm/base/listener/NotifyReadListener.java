package com.gj.llm.base.listener;

import com.gj.llm.base.service.NotifyService;
import com.gj.llm.netty.protocol.MessageEnvelope;
import com.gj.llm.netty.session.ClientSession;
import com.gj.llm.netty.spi.MessageListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 已读回执监听器 —— WS 上行 {@code notify.read} 的业务处理，
 * 演示基座 {@link MessageListener} SPI 的标准用法：
 * <ul>
 *   <li>身份取自 {@code session.principalId()}（派发线程无 SecurityContext，
 *       连接身份即事实身份，且归属校验仍以 user_id 等值过滤兜底）</li>
 *   <li>payload 结构由本模块定义：{@code {"ids": ["1","2"]}}（雪花 ID 以字符串上报，
 *       兼容数字形态）</li>
 *   <li>运行在每会话顺序虚拟线程上，可直接做阻塞 DB 操作</li>
 * </ul>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyReadListener implements MessageListener {

    private final NotifyService notifyService;

    @Override
    public String topic() {
        return NotifyService.TOPIC_READ;
    }

    @Override
    public void onMessage(ClientSession session, MessageEnvelope message) {
        if (!(message.payload() instanceof JsonNode payload)) {
            return;
        }
        JsonNode idsNode = payload.path("ids");
        if (!idsNode.isArray() || idsNode.size() == 0) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        idsNode.forEach(node -> {
            // 前端以字符串上报雪花 ID（全局 Long→String 的回传形态），兼容数字形态
            if (node.isNumber()) {
                ids.add(node.asLong());
            } else if (node.isTextual()) {
                try {
                    ids.add(Long.parseLong(node.asText().trim()));
                } catch (NumberFormatException ignored) {
                    // 非法 id 忽略
                }
            }
        });
        if (ids.isEmpty()) {
            return;
        }
        int updated = notifyService.markReadByPrincipal(session.principalId(), ids);
        log.debug("WS 已读回执处理完成: principal={}, 请求={}, 更新={}", session.principalId(), ids.size(), updated);
    }
}
