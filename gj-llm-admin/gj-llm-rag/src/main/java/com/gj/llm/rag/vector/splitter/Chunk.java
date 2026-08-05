package com.gj.llm.rag.vector.splitter;

import lombok.Data;
import org.springframework.ai.document.Document;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 切分产物 -- 一个子块及其所属父块信息。
 *
 * <p>父子召回的核心数据结构：子块（{@link #text}）作为检索单元被 embedding，
 * 命中后用父块（{@link #parentText}）作为 LLM 上下文，兼顾检索精度与上下文完整性。</p>
 *
 * @author zf
 */
@Data
public class Chunk {

    /** 子块文本（检索/embedding 单元，可能含上下文前缀）。 */
    private String text;

    /** 父块文本（命中子块后返回给 LLM 的完整上下文）。 */
    private final String parentText;

    /** 父块唯一标识，用于检索侧按父去重。 */
    private final String parentId;

    /** 子块在父块中的序号（调试用）。 */
    private final int chunkIndex;

    /** 继承自 reader-Document 的元数据（dataset_id/source/title 等）。 */
    private final Map<String, Object> metadata;

    public Chunk(String text, String parentText, String parentId, int chunkIndex, Map<String, Object> metadata) {
        this.text = text;
        this.parentText = parentText;
        this.parentId = parentId;
        this.chunkIndex = chunkIndex;
        this.metadata = metadata;
    }

    /** 在子块文本前拼接内容（用于上下文前缀注入）。 */
    public void prependText(String prefix) {
        this.text = prefix + this.text;
    }

    /**
     * 转为 Spring AI {@link Document} 以便复用现有 indexDocuments 管道。
     * parent_id / parent_content / chunk_index 一并放入 metadata，由 ES 服务写入对应字段。
     */
    public Document toDocument() {
        Map<String, Object> meta = new LinkedHashMap<>(metadata);
        meta.put("parent_id", parentId);
        meta.put("parent_content", parentText);
        meta.put("chunk_index", chunkIndex);
        return new Document(text, meta);
    }
}
