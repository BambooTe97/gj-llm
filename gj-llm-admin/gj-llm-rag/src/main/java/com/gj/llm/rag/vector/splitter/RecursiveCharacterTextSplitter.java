package com.gj.llm.rag.vector.splitter;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 递归字符文本切分器 -- 支持句子级 overlap 与短块合并。
 *
 * <p>类似 LangChain RecursiveCharacterTextSplitter：按分隔符层级递归切分，
 * 最后按字符切分。与旧实现相比的关键修正：</p>
 * <ul>
 *   <li><b>overlap 边界感知</b>：取上一段的尾部完整句子拼到下一段开头，
 *       不再把 chunks 拼回原文做盲字符滑窗（旧实现会让递归边界切分失效）。</li>
 *   <li><b>短块合并非丢弃</b>：过短块并入相邻块，不再静默丢弃（避免数据丢失）。</li>
 *   <li><b>codepoint 安全</b>：字符兜底切分使用 {@code offsetByCodePoints}，
 *       不会从代理对中间切断 emoji/生僻字。</li>
 * </ul>
 *
 * <p>核心方法 {@link #splitIntoSegments}、{@link #trailingSentences}、
 * {@link #mergeShort} 为包级静态，供 {@link ParentChildSplitter} 复用。</p>
 */
public class RecursiveCharacterTextSplitter {

    /**
     * 默认分隔符层级（从粗到细）：段落 -&gt; 行 -&gt; 中文句末 -&gt; 分号/逗号 -&gt;
     * 英文句末 -&gt; 冒号 -&gt; 空格 -&gt; 字符兜底。
     */
    private static final List<String> DEFAULT_SEPARATORS = List.of(
            "\n\n", "\n", "。", "！", "？", "；", "，", ".", "!", "?", ";", ",", ":", " ", ""
    );

    /** 句末标点（中英文），用于 overlap 时切出完整句子。 */
    private static final Pattern SENTENCE_END = Pattern.compile("[。！？…!?]+");

    private final int chunkSize;
    private final int chunkOverlap;
    private final int minChunkLength;
    private final List<String> separators;

    public RecursiveCharacterTextSplitter(int chunkSize, int chunkOverlap, int minChunkLength) {
        this(chunkSize, chunkOverlap, minChunkLength, DEFAULT_SEPARATORS);
    }

    public RecursiveCharacterTextSplitter(int chunkSize, int chunkOverlap, int minChunkLength, List<String> separators) {
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap must be < chunkSize");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.minChunkLength = minChunkLength;
        this.separators = separators;
    }

    public List<Document> split(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            List<String> chunkTexts = splitText(doc.getText());
            for (String chunkText : chunkTexts) {
                Document splitDoc = new Document(chunkText);
                splitDoc.getMetadata().putAll(doc.getMetadata());
                result.add(splitDoc);
            }
        }
        return result;
    }

    List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> segments = splitIntoSegments(text, chunkSize, separators);
        return applyOverlap(segments);
    }

    /**
     * 递归降级分隔符切分：返回边界感知、无 overlap、每段 &lt;= maxSize 的片段列表。
     * 先尝试粗分隔符（段落），切不动或片段仍超长时降级到更细分隔符，最终字符兜底。
     *
     * @param text       待切分文本
     * @param maxSize    单段最大字符数
     * @param separators 分隔符层级（从粗到细，末尾通常为 "" 表示字符兜底）
     * @return 边界感知的片段列表
     */
    static List<String> splitIntoSegments(String text, int maxSize, List<String> separators) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (text.length() <= maxSize) {
            return text.isBlank() ? List.of() : List.of(text);
        }
        for (int i = 0; i < separators.size(); i++) {
            String separator = separators.get(i);
            if (separator.isEmpty()) {
                continue;
            }
            String[] parts = text.split(Pattern.quote(separator), -1);
            // 该分隔符未实际切分（整段无此分隔符）-> 尝试下一级
            if (parts.length <= 1) {
                continue;
            }
            return accumulate(parts, separator, maxSize, separators, i + 1);
        }
        // 所有分隔符都切不动（单段超长）-> codepoint 安全的字符兜底
        return splitByCharacters(text, maxSize);
    }

    private static List<String> accumulate(String[] parts, String separator, int maxSize,
                                           List<String> separators, int nextSepIndex) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String withSep = i < parts.length - 1 ? part + separator : part;

            if (current.length() + withSep.length() <= maxSize) {
                current.append(withSep);
            } else {
                if (current.length() > 0) {
                    result.add(current.toString());
                }
                if (withSep.length() <= maxSize) {
                    current = new StringBuilder(withSep);
                } else {
                    // 片段本身超长，递归用更细分隔符切分
                    List<String> sub = splitIntoSegments(withSep, maxSize,
                            separators.subList(nextSepIndex, separators.size()));
                    for (int j = 0; j < sub.size() - 1; j++) {
                        result.add(sub.get(j));
                    }
                    current = new StringBuilder(sub.isEmpty() ? "" : sub.get(sub.size() - 1));
                }
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static List<String> splitByCharacters(String text, int maxSize) {
        List<String> chunks = new ArrayList<>();
        int pos = 0;
        int total = text.codePointCount(0, text.length());
        while (pos < text.length()) {
            int remaining = text.codePointCount(pos, text.length());
            int step = Math.min(maxSize, remaining);
            int end = text.offsetByCodePoints(pos, step);
            chunks.add(text.substring(pos, end));
            pos = end;
        }
        return chunks;
    }

    /**
     * 句子级 overlap：将上一段的尾部完整句子拼到下一段开头（边界感知）。
     * 替代旧的盲字符滑窗，避免在词/句中间硬切。
     */
    private List<String> applyOverlap(List<String> segments) {
        return applySentenceOverlap(segments, chunkOverlap, minChunkLength);
    }

    /**
     * 句子级 overlap：将上一段尾部完整句子拼到下一段开头（边界感知）。
     * 包级静态，供 {@link ParentChildSplitter} 复用。
     *
     * @param segments 已按边界切好的无 overlap 片段
     * @param overlap  overlap 最大字符数（&lt;= 0 表示不重叠）
     * @param minLen   短块合并阈值
     * @return 带 overlap 与短块合并后的片段列表
     */
    static List<String> applySentenceOverlap(List<String> segments, int overlap, int minLen) {
        if (segments.isEmpty()) {
            return segments;
        }
        if (overlap <= 0) {
            return mergeShort(segments, minLen);
        }
        List<String> result = new ArrayList<>(segments.size());
        String prevTail = "";
        for (String seg : segments) {
            String combined = (prevTail.isEmpty() || seg.startsWith(prevTail)) ? seg : prevTail + seg;
            result.add(combined);
            prevTail = trailingSentences(combined, overlap);
        }
        return mergeShort(result, minLen);
    }

    /**
     * 取文本末尾的若干完整句子，总长不超过 maxLen（用于 overlap）。
     * 若单句已超 maxLen，则取该句末尾 maxLen 字符（codepoint 安全）。
     *
     * @param text   源文本
     * @param maxLen 最大长度（字符数）
     * @return 末尾句子串，可能为空
     */
    static String trailingSentences(String text, int maxLen) {
        if (text == null || text.isEmpty() || maxLen <= 0) {
            return "";
        }
        List<String> sentences = new ArrayList<>();
        Matcher m = SENTENCE_END.matcher(text);
        int last = 0;
        while (m.find()) {
            sentences.add(text.substring(last, m.end()));
            last = m.end();
        }
        if (last < text.length()) {
            sentences.add(text.substring(last));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = sentences.size() - 1; i >= 0; i--) {
            String s = sentences.get(i);
            if (sb.length() + s.length() <= maxLen) {
                sb.insert(0, s);
            } else {
                // 该句加入会超限：若 sb 仍空，取该句末尾 maxLen 字符兜底
                if (sb.length() == 0) {
                    int cp = s.codePointCount(0, s.length());
                    int start = s.offsetByCodePoints(s.length(), -Math.min(maxLen, cp));
                    return s.substring(start);
                }
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 过短块合并：长度 &lt; minLen 的块并入前一块（首块过短则并入后一块），不丢弃。
     */
    static List<String> mergeShort(List<String> segments, int minLen) {
        if (segments.isEmpty()) {
            return segments;
        }
        List<String> merged = new ArrayList<>();
        for (String seg : segments) {
            if (seg.length() < minLen && !merged.isEmpty()) {
                int last = merged.size() - 1;
                merged.set(last, merged.get(last) + seg);
            } else {
                merged.add(seg);
            }
        }
        // 首块过短则并入第二块
        if (merged.size() > 1 && merged.get(0).length() < minLen) {
            merged.set(1, merged.get(0) + merged.get(1));
            merged.remove(0);
        }
        return merged;
    }
}
