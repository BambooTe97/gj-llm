package com.gj.llm.rag.vector.splitter;

import java.util.List;
import java.util.Set;

/**
 * 按文件扩展名选择切分分隔符层级 -- 不同内容类型用不同边界，避免在代码/结构化数据上套用散文标点。
 *
 * <p>例如对源码用 {@code {};} 切分会破坏结构，对 JSON 用对象/数组边界，
 * 对 CSV 按行切分保持记录完整，散文用中英文句末标点。</p>
 *
 * @author zf
 */
public final class ChunkSeparators {

    private ChunkSeparators() {
    }

    /** 散文（PDF/Markdown/Office/TXT 等）：中文为主、兼容英文句末标点。 */
    public static final List<String> PROSE = List.of(
            "\n\n", "\n", "。", "！", "？", "；", "，", ".", "!", "?", ";", ",", ":", " ", "");

    /** 源码：按空行 / 行 / 语句块边界切分。 */
    public static final List<String> CODE = List.of("\n\n", "\n", "}", "{", ";", " ", "");

    /** JSON：按对象 / 数组边界切分。 */
    public static final List<String> JSON = List.of("\n\n", "\n", "}", "{", ",", " ", "");

    /** CSV：按行切分（保持单行完整，超长行才字符兜底）。 */
    public static final List<String> CSV = List.of(
            "\n", "\r\n", "");

    /** 走代码切分策略的扩展名（含配置/日志，避免在含 {@code .} {@code :} 的 key/url 上误切）。 */
    private static final Set<String> CODE_EXTS = Set.of(
            "java", "py", "js", "ts", "css", "sql", "sh", "bat", "gradle",
            "properties", "ini", "cfg", "xml", "yml", "yaml", "log");

    /**
     * 根据扩展名返回合适的分隔符层级。
     *
     * @param extension 扩展名（不含点号，大小写不敏感），null 时返回散文策略
     */
    public static List<String> forExtension(String extension) {
        if (extension == null) {
            return PROSE;
        }
        String ext = extension.toLowerCase();
        if ("json".equals(ext)) {
            return JSON;
        }
        if ("csv".equals(ext)) {
            return CSV;
        }
        if (CODE_EXTS.contains(ext)) {
            return CODE;
        }
        return PROSE;
    }
}
