package com.gj.llm.rag.vector.reader;

import com.gj.llm.file.model.FileInfo;
import com.gj.llm.rag.constant.FileReaderCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Apache Tika 文档内容读取器 —— 自动检测格式并提取纯文本。
 *
 * <p>基于 {@link org.apache.tika.parser.AutoDetectParser}，无需声明具体格式即可解析。
 * 覆盖 Office 文档、网页、电子书等十余种格式：
 * doc/docx/ppt/pptx/xls/xlsx/html/htm/epub/odt/rtf。</p>
 *
 * <p>输出的 Document 为整篇文档的纯文本，后续由 RecursiveCharacterTextSplitter 统一切分。
 * PDF 不在此 Reader 范围内，由 PdfContentReader 按段落/页面结构化处理。</p>
 *
 * @author zf
 */
@Slf4j
@Component
public class TikaContentReader implements FileContentReader {

    @Override
    public boolean supports(String extension) {
        return FileReaderCategory.TIKA.supports(extension);
    }

    @Override
    public List<Document> read(Resource resource, FileInfo fileInfo) {
        try {
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();
            log.info("Tika 读取成功: {}, format={}, contentLength={}",
                    fileInfo.getOriginalName(),
                    fileInfo.getExtension(),
                    documents.isEmpty() ? 0 : documents.getFirst().getText().length());
            return documents;
        } catch (Exception e) {
            log.error("Tika 读取失败: {} - {}", fileInfo.getOriginalName(), e.getMessage());
            return List.of();
        }
    }
}
