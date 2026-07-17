package com.gj.llm.rag.vector.splitter;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 递归字符文本切分器 —— 支持 chunkOverlap。
 *
 * <p>类似 LangChain RecursiveCharacterTextSplitter：
 * 按分隔符层级递归切分，最后按字符切分，合并过短的 chunk 并叠加 overlap。</p>
 */
public class RecursiveCharacterTextSplitter {

    private static final List<String> DEFAULT_SEPARATORS = List.of(
            "\n\n", "\n", "。", "！", "？", "；", "，", " ", ""
    );

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

    private List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = recursiveSplit(text, 0);
        return applyOverlap(chunks);
    }

    private List<String> recursiveSplit(String text, int separatorIndex) {
        if (text.length() <= chunkSize || separatorIndex >= separators.size()) {
            // 最后一级：按字符强制切分
            if (text.length() <= chunkSize) {
                return text.isBlank() ? List.of() : List.of(text);
            }
            return splitByCharacters(text);
        }

        String separator = separators.get(separatorIndex);
        List<String> result = new ArrayList<>();

        if (separator.isEmpty()) {
            return splitByCharacters(text);
        }

        String[] parts = text.split(Pattern.quote(separator), -1);
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String withSep = i < parts.length - 1 ? part + separator : part;

            if (current.length() + withSep.length() <= chunkSize) {
                current.append(withSep);
            } else {
                // 当前累积的 chunk 先提交
                if (current.length() > 0) {
                    result.add(current.toString());
                }
                // 新片段：如果自身 ≤ chunkSize，作为新 chunk 的起点
                if (withSep.length() <= chunkSize) {
                    current = new StringBuilder(withSep);
                } else {
                    // 片段本身 > chunkSize，递归用更细的分隔符切分
                    List<String> subChunks = recursiveSplit(withSep, separatorIndex + 1);
                    // 除了最后一个 sub-chunk，其余直接提交
                    for (int j = 0; j < subChunks.size() - 1; j++) {
                        result.add(subChunks.get(j));
                    }
                    current = new StringBuilder(subChunks.isEmpty() ? "" : subChunks.get(subChunks.size() - 1));
                }
            }
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }

    private List<String> splitByCharacters(String text) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
        }
        return chunks;
    }

    /**
     * 标准滑动窗口重叠：将 semantic chunks 拼接为连续文本后，以 chunkSize 为窗口、
     * chunkOverlap 为步进偏移量生成固定大小的检索块。
     *
     * <p>例如 chunkSize=600, overlap=150：
     * 块1: text[0:600], 块2: text[450:1050], 块3: text[900:1500] ...</p>
     */
    private List<String> applyOverlap(List<String> chunks) {
        if (chunkOverlap == 0 || chunks.size() <= 1) {
            return filterShort(chunks);
        }

        // 拼接为连续文本
        StringBuilder sb = new StringBuilder();
        for (String chunk : chunks) {
            sb.append(chunk);
        }
        String text = sb.toString();

        // 滑动窗口
        List<String> overlapped = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + chunkSize, text.length());
            overlapped.add(text.substring(pos, end));
            if (end >= text.length()) break;
            pos = end - chunkOverlap;
        }

        return filterShort(overlapped);
    }

    private List<String> filterShort(List<String> chunks) {
        return chunks.stream()
                .filter(c -> c.length() >= minChunkLength)
                .toList();
    }
}
