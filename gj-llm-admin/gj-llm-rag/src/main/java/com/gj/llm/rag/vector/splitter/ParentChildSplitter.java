package com.gj.llm.rag.vector.splitter;

import com.gj.llm.common.util.StringUtils;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 父子切分器 -- 两级粒度切分，支持父子召回（small-to-big）。
 *
 * <p>对每个 reader-Document：先切成边界感知的<b>父块</b>（parentSize，无 overlap），
 * 每个父块再切成带句子级 overlap 的<b>子块</b>（childSize）。子块用于精确检索，
 * 父块作为命中后返回 LLM 的完整上下文。</p>
 *
 * <p>复用 {@link RecursiveCharacterTextSplitter} 的边界切分与 overlap 核心逻辑，DRY。</p>
 *
 * @author zf
 */
public class ParentChildSplitter {

    private final int parentSize;
    private final int childSize;
    private final int childOverlap;
    private final int minChunkLength;
    private final List<String> separators;

    public ParentChildSplitter(int parentSize, int childSize, int childOverlap,
                               int minChunkLength, List<String> separators) {
        if (parentSize <= 0 || childSize <= 0) {
            throw new IllegalArgumentException("parentSize/childSize must be > 0");
        }
        if (childSize > parentSize) {
            throw new IllegalArgumentException("childSize must be <= parentSize");
        }
        if (childOverlap >= childSize) {
            throw new IllegalArgumentException("childOverlap must be < childSize");
        }
        this.parentSize = parentSize;
        this.childSize = childSize;
        this.childOverlap = childOverlap;
        this.minChunkLength = minChunkLength;
        this.separators = separators;
    }

    /**
     * 切分多个 reader-Document 为带父子关系的子块列表。
     */
    public List<Chunk> split(List<Document> documents) {
        List<Chunk> result = new ArrayList<>();
        for (Document doc : documents) {
            String fullText = doc.getText();
            if (StringUtils.isBlank(fullText)) {
                continue;
            }
            Map<String, Object> baseMeta = doc.getMetadata();

            // 父块：边界感知、无 overlap（靠段落分隔符自然对齐）
            List<String> parents = RecursiveCharacterTextSplitter.splitIntoSegments(fullText, parentSize, separators);
            for (String parentText : parents) {
                String parentId = UUID.randomUUID().toString();

                // 子块：边界感知 + 句子级 overlap + 短块合并
                List<String> children = RecursiveCharacterTextSplitter.splitIntoSegments(parentText, childSize, separators);
                children = RecursiveCharacterTextSplitter.applySentenceOverlap(children, childOverlap, minChunkLength);

                for (int ci = 0; ci < children.size(); ci++) {
                    result.add(new Chunk(children.get(ci), parentText, parentId, ci, new LinkedHashMap<>(baseMeta)));
                }
            }
        }
        return result;
    }
}
