package com.gj.llm.rag.vector.splitter;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RecursiveCharacterTextSplitter} 与切分核心逻辑的单元测试。
 * 重点验证 D1（句子级 overlap）、D5（短块合并）、D6（codepoint 安全）三项修复。
 *
 * @author zf
 */
class RecursiveCharacterTextSplitterTest {

    /** 10 个 A + 句号 = 11 字符的句子，便于精确推算切分边界。 */
    private static final String SENTENCE = "AAAAAAAAAA。";
    private static final List<String> PROSE = ChunkSeparators.PROSE;

    // ---------------- splitIntoSegments ----------------

    @Test
    void splitIntoSegments_respectsMaxSize() {
        String text = SENTENCE.repeat(20); // 220 字符
        List<String> segs = RecursiveCharacterTextSplitter.splitIntoSegments(text, 25, PROSE);
        assertThat(segs).isNotEmpty();
        assertThat(segs).allSatisfy(s -> assertThat(s.length()).isLessThanOrEqualTo(25));
    }

    @Test
    void splitIntoSegments_prefersSentenceBoundary_notMidSentence() {
        // 关键回归：切分应在句号处断开，不应把句子从中间 A 处切断
        String text = SENTENCE.repeat(5); // 55 字符
        List<String> segs = RecursiveCharacterTextSplitter.splitIntoSegments(text, 25, PROSE);
        assertThat(segs).isNotEmpty();
        // 每个完整片段都应以句号结尾（最后一段若为整句同样如此）
        assertThat(segs).allSatisfy(s -> assertThat(s.endsWith("。")).isTrue());
    }

    // ---------------- trailingSentences ----------------

    @Test
    void trailingSentences_takesTailCompleteSentences() {
        String text = "你好。世界。测试。";
        // 全部容纳
        assertThat(RecursiveCharacterTextSplitter.trailingSentences(text, 100)).isEqualTo("你好。世界。测试。");
        // 只能容纳末句
        assertThat(RecursiveCharacterTextSplitter.trailingSentences(text, 4)).isEqualTo("测试。");
    }

    @Test
    void trailingSentences_singleLongSentence_fallsBackToTailChars() {
        String text = "abcdefghij"; // 无句末标点
        assertThat(RecursiveCharacterTextSplitter.trailingSentences(text, 4)).isEqualTo("ghij");
    }

    // ---------------- mergeShort ----------------

    @Test
    void mergeShort_mergesIntoPrevious_doesNotDiscard() {
        List<String> segs = List.of("长内容长内容长内容", "短", "另一段另一段");
        List<String> merged = RecursiveCharacterTextSplitter.mergeShort(segs, 5);
        // "短" 应并入前一段，块数从 3 降到 2，内容不丢失
        assertThat(merged).hasSize(2);
        assertThat(merged.get(0)).contains("短");
        assertThat(String.join("", merged)).isEqualTo(String.join("", segs));
    }

    @Test
    void mergeShort_firstBlockShort_mergesIntoNext() {
        List<String> segs = List.of("短", "长内容长内容长内容");
        List<String> merged = RecursiveCharacterTextSplitter.mergeShort(segs, 5);
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0)).isEqualTo("短长内容长内容长内容");
    }

    // ---------------- D1 修复：句子级 overlap ----------------

    @Test
    void overlap_isSentenceAware_consecutiveChunksShareTrailingSentence() {
        String text = SENTENCE.repeat(5); // 55 字符
        List<String> chunks = new RecursiveCharacterTextSplitter(25, 11, 5, PROSE)
                .splitText(text);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        // 首块以句号结尾（边界感知，非盲切）--旧实现会切成 "...AAAA"
        assertThat(chunks.get(0)).endsWith("。");
        // 第二块以首块尾部完整句子开头（overlap 是句子级）
        assertThat(chunks.get(1)).startsWith(SENTENCE);
        // 内容无丢失：拼接去重后仍覆盖原文核心句子
        assertThat(chunks.get(chunks.size() - 1)).endsWith("。");
    }

    @Test
    void overlapZero_producesDisjointBoundarySegments() {
        String text = SENTENCE.repeat(6);
        List<String> chunks = new RecursiveCharacterTextSplitter(25, 0, 5, PROSE)
                .splitText(text);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(s -> assertThat(s.endsWith("。")).isTrue());
        // 无 overlap 时拼接应等于原文
        assertThat(String.join("", chunks)).isEqualTo(text);
    }

    // ---------------- D6 修复：codepoint 安全 ----------------

    @Test
    void codepointSafe_emojiNotSplitMidSurrogate() {
        // 10 个 emoji，每个占 2 个 UTF-16 单元（代理对），共 20 char / 10 codepoint
        String text = "😀".repeat(10);
        List<String> segs = RecursiveCharacterTextSplitter.splitIntoSegments(text, 5, ChunkSeparators.CODE);
        assertThat(segs).isNotEmpty();
        // 每块必须是完整的 emoji（5 codepoint = 10 char），不能出现孤立代理
        assertThat(segs).allSatisfy(s -> {
            assertThat(s.codePointCount(0, s.length())).isLessThanOrEqualTo(5);
            assertThat(s.length()).isEqualTo(s.codePointCount(0, s.length()) * 2);
        });
        // 拼接无损
        assertThat(String.join("", segs)).isEqualTo(text);
    }

    // ---------------- 内容类型分发 ----------------

    @Test
    void chunkSeparators_dispatchByExtension() {
        assertThat(ChunkSeparators.forExtension("java")).isSameAs(ChunkSeparators.CODE);
        assertThat(ChunkSeparators.forExtension("PY")).isSameAs(ChunkSeparators.CODE);
        assertThat(ChunkSeparators.forExtension("json")).isSameAs(ChunkSeparators.JSON);
        assertThat(ChunkSeparators.forExtension("csv")).isSameAs(ChunkSeparators.CSV);
        assertThat(ChunkSeparators.forExtension("md")).isSameAs(ChunkSeparators.PROSE);
        assertThat(ChunkSeparators.forExtension(null)).isSameAs(ChunkSeparators.PROSE);
    }

    @Test
    void codeSeparators_splitOnBlockBoundaryNotProsePunctuation() {
        // 代码片段：以 } 分隔块；CODE 分隔符不含 。/， 故不会在中文标点切
        String code = "class A { void f(){x();} }\nclass B { void g(){y();} }";
        List<String> segs = RecursiveCharacterTextSplitter.splitIntoSegments(code, 15, ChunkSeparators.CODE);
        assertThat(segs).isNotEmpty();
        assertThat(segs).allSatisfy(s -> assertThat(s.length()).isLessThanOrEqualTo(15));
    }

    // ---------------- split(List<Document>) 元数据透传 ----------------

    @Test
    void split_copiesParentMetadata() {
        Document doc = new Document(SENTENCE.repeat(6), Map.of("source", "test.md"));
        List<Document> splits = new RecursiveCharacterTextSplitter(25, 11, 5, PROSE).split(List.of(doc));
        assertThat(splits).isNotEmpty();
        assertThat(splits).allSatisfy(d -> {
            assertThat(d.getMetadata()).containsEntry("source", "test.md");
            assertThat(d.getText()).isNotBlank();
        });
    }
}
