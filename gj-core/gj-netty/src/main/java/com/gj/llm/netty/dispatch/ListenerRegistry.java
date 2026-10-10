package com.gj.llm.netty.dispatch;

import com.gj.llm.common.util.StringUtils;
import com.gj.llm.netty.protocol.Topic;
import com.gj.llm.netty.spi.MessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 上行消息监听器注册表 —— 构造时自动收集全部 {@link MessageListener} bean，
 * 运行期按 topic 匹配（精确 + 前缀通配 {@code "chat.*"}）。
 *
 * <p>只读热路径（匹配）无锁：注册发生在启动期，热路径仅查
 * {@link ConcurrentHashMap} + 遍历不可变列表。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "gj.netty", name = "enabled", havingValue = "true")
public class ListenerRegistry {

    /** topic → 精确匹配的监听器 */
    private final Map<String, List<MessageListener>> exact = new ConcurrentHashMap<>();

    /** 前缀（含尾部点，如 "chat."）→ 前缀通配监听器 */
    private final Map<String, List<MessageListener>> prefix = new ConcurrentHashMap<>();

    public ListenerRegistry(ObjectProvider<MessageListener> listeners) {
        listeners.orderedStream().forEach(this::register);
    }

    /**
     * 注册监听器（启动期由构造器自动调用，业务只需声明 bean）。
     */
    public void register(MessageListener listener) {
        String topic = listener.topic();
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("MessageListener topic 不能为空: " + listener.getClass().getName());
        }
        if (topic.startsWith(Topic.SYS_PREFIX)) {
            throw new IllegalArgumentException("MessageListener 不得监听保留命名空间 sys.*: " + topic);
        }
        if (topic.endsWith(".*")) {
            String p = topic.substring(0, topic.length() - 1).toLowerCase(Locale.ROOT);
            prefix.computeIfAbsent(p, k -> new CopyOnWriteArrayList<>()).add(listener);
        } else {
            exact.computeIfAbsent(topic.toLowerCase(Locale.ROOT), k -> new CopyOnWriteArrayList<>()).add(listener);
        }
        log.info("gj-netty 注册上行监听器 topic={} listener={}", topic, listener.getClass().getName());
    }

    /**
     * 匹配 topic 对应的监听器（无匹配返回空列表）。
     */
    public List<MessageListener> match(String topic) {
        String t = topic.toLowerCase(Locale.ROOT);
        List<MessageListener> result = exact.get(t);
        List<MessageListener> matched = result == null ? List.of() : result;
        for (Map.Entry<String, List<MessageListener>> entry : prefix.entrySet()) {
            if (t.startsWith(entry.getKey())) {
                matched = concat(matched, entry.getValue());
            }
        }
        return matched;
    }

    private static List<MessageListener> concat(List<MessageListener> a, List<MessageListener> b) {
        if (a.isEmpty()) {
            return b;
        }
        return List.copyOf(java.util.stream.Stream.concat(a.stream(), b.stream()).toList());
    }
}
