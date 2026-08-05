package com.gj.llm.rag.vector.splitter;

import com.gj.llm.rag.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 确定性上下文注入器 -- 给每个子块文本前拼接文档标识前缀（零 LLM 成本）。
 *
 * <p>前缀形如 {@code [文档: <source> > <title>]}，进 ES content 字段后 BM25 与 embedding
 * 同时吃到标题/来源关键词，让 chunk 不再是无来源的孤儿片段。title 取自 Markdown reader
 * 的 heading metadata；PDF/Tika/Text 无 title 时仅用 source。</p>
 *
 * @author zf
 */
@Component
public class ChunkContextEnricher {

    private final RagProperties props;

    public ChunkContextEnricher(RagProperties props) {
        this.props = props;
    }

    /**
     * 原地给子块文本前拼接上下文前缀。开关关闭或无 chunk 时直接返回。
     */
    public void enrich(List<Chunk> chunks) {
        if (!props.isContextPrefixEnabled() || chunks == null || chunks.isEmpty()) {
            return;
        }
        for (Chunk c : chunks) {
            Map<String, Object> meta = c.getMetadata();
            String source = str(meta.get("source"));
            String title = str(meta.get("title"));
            String prefix = (title != null && !title.isBlank())
                    ? "[文档: " + source + " > " + title + "]\n"
                    : "[文档: " + source + "]\n";
            c.prependText(prefix);
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
