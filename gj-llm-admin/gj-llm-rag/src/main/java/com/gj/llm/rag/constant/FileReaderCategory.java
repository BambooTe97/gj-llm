package com.gj.llm.rag.constant;

import com.gj.llm.file.constant.FileTypeEnum;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文件读取器分类枚举 —— 按 ContentReader 实现划分，持有对应文件类型的扩展名集合。
 *
 * <p>每个 ContentReader 通过委托本枚举的 {@link #supports(String)} 判断是否处理某文件，
 * 无需各自维护 {@code SUPPORTED_EXTENSIONS} 常量。</p>
 *
 * @author zf
 */
public enum FileReaderCategory {

    /** PDF —— PdfContentReader，优先段落、回退按页 */
    PDF(FileTypeEnum.PDF),

    /** Markdown —— MarkdownContentReader，基于 commonmark AST 按标题切分 */
    MARKDOWN(FileTypeEnum.MD, FileTypeEnum.MARKDOWN),

    /** Office / Web / 电子书 —— TikaContentReader，AutoDetectParser 自动解析 */
    TIKA(
            FileTypeEnum.DOC, FileTypeEnum.DOCX,
            FileTypeEnum.PPT, FileTypeEnum.PPTX,
            FileTypeEnum.XLS, FileTypeEnum.XLSX,
            FileTypeEnum.HTML, FileTypeEnum.HTM,
            FileTypeEnum.EPUB, FileTypeEnum.ODT,
            FileTypeEnum.RTF
    ),

    /** 纯文本 / 源码 / 配置 / 脚本 —— TextContentReader，UTF-8 全文读取 */
    TEXT(
            FileTypeEnum.TXT,
            FileTypeEnum.JSON, FileTypeEnum.XML, FileTypeEnum.CSV,
            FileTypeEnum.YML, FileTypeEnum.YAML,
            FileTypeEnum.JAVA, FileTypeEnum.PY, FileTypeEnum.JS,
            FileTypeEnum.TS, FileTypeEnum.CSS, FileTypeEnum.SQL,
            FileTypeEnum.PROPERTIES, FileTypeEnum.INI, FileTypeEnum.CFG,
            FileTypeEnum.LOG, FileTypeEnum.SH, FileTypeEnum.BAT,
            FileTypeEnum.GRADLE
    );

    private final Set<String> extensions;

    FileReaderCategory(FileTypeEnum... types) {
        this.extensions = Arrays.stream(types)
                .map(FileTypeEnum::getExtension)
                .collect(Collectors.toSet());
    }

    /**
     * 判断给定扩展名是否属于当前分类（内部转为小写比较，对大小写不敏感）。
     *
     * @param extension 文件扩展名（不含点号，大小写均可）
     */
    public boolean supports(String extension) {
        return extension != null && extensions.contains(extension);
    }
}
