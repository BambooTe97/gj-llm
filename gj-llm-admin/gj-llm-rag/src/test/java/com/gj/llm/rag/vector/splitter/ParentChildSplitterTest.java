package com.gj.llm.rag.vector.splitter;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ParentChildSplitter} 父子切分逻辑测试。
 *
 * @author zf
 */
class ParentChildSplitterTest {

    private static final String SENTENCE = "AAAAAAAAAA。";
    private static final List<String> PROSE = ChunkSeparators.PROSE;

    @Test
    void split_producesParentChildStructure() {
        String text = SENTENCE.repeat(12); // 132 字符
        ParentChildSplitter splitter = new ParentChildSplitter(50, 25, 11, 5, PROSE);
        List<Chunk> chunks = splitter.split(List.of(new Document(text)));

        assertThat(chunks).isNotEmpty();
        // 每个子块都带父块信息
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.getParentId()).isNotNull();
            assertThat(c.getParentText()).isNotBlank();
            assertThat(c.getMetadata()).isNotNull();
        });
    }

    @Test
    void childrenOfSameParent_shareParentId_andContiguousIndex() {
        String text = SENTENCE.repeat(12);
        ParentChildSplitter splitter = new ParentChildSplitter(50, 25, 11, 5, PROSE);
        List<Chunk> chunks = splitter.split(List.of(new Document(text)));

        // 按 parentId 分组
        Map<String, List<Chunk>> groups = new LinkedHashMap<>();
        for (Chunk c : chunks) {
            groups.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        assertThat(groups.size()).isGreaterThanOrEqualTo(2); // 至少两个父块

        // 每组内 chunkIndex 连续从 0 开始，parentText 相同
        groups.values().forEach(group -> {
            assertThat(group.get(0).getChunkIndex()).isZero();
            for (int i = 0; i < group.size(); i++) {
                assertThat(group.get(i).getChunkIndex()).isEqualTo(i);
                assertThat(group.get(i).getParentText()).isEqualTo(group.get(0).getParentText());
            }
        });
    }

    @Test
    void childText_isSubstringOfParentText() {
        // overlap 拼接的是父块内的尾部句子，故子块文本应整体落在父块文本内
        String text = SENTENCE.repeat(10);
        ParentChildSplitter splitter = new ParentChildSplitter(50, 25, 11, 5, PROSE);
        List<Chunk> chunks = splitter.split(List.of(new Document(text)));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(c ->
                assertThat(c.getParentText()).contains(c.getText()));
    }

    @Test
    void toDocument_carriesParentFieldsInMetadata() {
        String text = SENTENCE.repeat(6);
        ParentChildSplitter splitter = new ParentChildSplitter(50, 25, 11, 5, PROSE);
        List<Chunk> chunks = splitter.split(List.of(new Document(text)));

        Document doc = chunks.get(0).toDocument();
        assertThat(doc.getMetadata()).containsKeys("parent_id", "parent_content", "chunk_index");
        assertThat(doc.getText()).isNotBlank();
    }

    @Test
    void parentSizeRespected() {
        String text = SENTENCE.repeat(20);
        ParentChildSplitter splitter = new ParentChildSplitter(40, 20, 5, 5, PROSE);
        List<Chunk> chunks = splitter.split(List.of(new Document(text)));

        // 父块文本不应超过 parentSize（边界感知切分保证）
        assertThat(chunks).allSatisfy(c -> assertThat(c.getParentText().length()).isLessThanOrEqualTo(40));
    }
}
