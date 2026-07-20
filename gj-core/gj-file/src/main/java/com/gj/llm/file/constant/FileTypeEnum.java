package com.gj.llm.file.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统支持的文件类型枚举 —— 唯一真相源，上传校验和所有 ContentReader 统一引用。
 *
 * @author gj-llm
 */
@Getter
public enum FileTypeEnum {

    // ==================== PDF ====================
    PDF("pdf"),

    // ==================== Tika（Office 文档）====================
    DOC("doc"),
    DOCX("docx"),
    PPT("ppt"),
    PPTX("pptx"),
    XLS("xls"),
    XLSX("xlsx"),

    // ==================== Tika（Web / 电子书）====================
    HTML("html"),
    HTM("htm"),
    EPUB("epub"),
    ODT("odt"),
    RTF("rtf"),

    // ==================== Markdown ====================
    MD("md"),
    MARKDOWN("markdown"),

    // ==================== 纯文本 ====================
    TXT("txt"),
    JSON("json"),
    XML("xml"),
    CSV("csv"),
    YML("yml"),
    YAML("yaml"),

    // ==================== 源代码 ====================
    JAVA("java"),
    PY("py"),
    JS("js"),
    TS("ts"),
    CSS("css"),
    SQL("sql"),

    // ==================== 配置 / 日志 / 脚本 ====================
    PROPERTIES("properties"),
    INI("ini"),
    CFG("cfg"),
    LOG("log"),
    SH("sh"),
    BAT("bat"),
    GRADLE("gradle"),

    // ==================== 图片（仅上传，不参与 RAG 向量化）====================
    PNG("png"),
    JPG("jpg"),
    JPEG("jpeg"),
    GIF("gif");

    private final String extension;

    FileTypeEnum(String extension) {
        this.extension = extension;
    }

    /**
     * 根据扩展名字符串（小写，不含点号）查找枚举。
     */
    public static Optional<FileTypeEnum> fromExtension(String ext) {
        if (ext == null || ext.isEmpty()) {
            return Optional.empty();
        }
        String lower = ext.toLowerCase();
        return Arrays.stream(values())
                .filter(e -> e.extension.equals(lower))
                .findFirst();
    }

    /**
     * 返回全部扩展名字符串集合。
     */
    public static Set<String> allExtensions() {
        return Arrays.stream(values())
                .map(FileTypeEnum::getExtension)
                .collect(Collectors.toSet());
    }
}
