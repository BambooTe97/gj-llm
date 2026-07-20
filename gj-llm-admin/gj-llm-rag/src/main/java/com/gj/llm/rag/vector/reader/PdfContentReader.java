package com.gj.llm.rag.vector.reader;

import com.gj.llm.file.model.FileInfo;
import com.gj.llm.rag.constant.FileReaderCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PDF 文件内容读取器 —— 优先按段落解析，回退到按页解析。
 *
 * <p>两级策略：
 * <ol>
 *   <li>ParagraphPdfDocumentReader 尝试按段落提取（保留结构化信息）</li>
 *   <li>解析失败时回退到 PagePdfDocumentReader 按页读取</li>
 * </ol>
 *
 * <p>注意：TikaContentReader 也能读 PDF，但不会保留段落结构，
 * 因此 PDF 统一走此专用读取器。</p>
 *
 * @author zf
 */
@Slf4j
@Component
public class PdfContentReader implements FileContentReader {

    @Override
    public boolean supports(String extension) {
        return FileReaderCategory.PDF.supports(extension);
    }

    @Override
    public List<Document> read(Resource resource, FileInfo fileInfo) {
        try {
            ParagraphPdfDocumentReader reader = new ParagraphPdfDocumentReader(resource);
            List<Document> documents = reader.get();
            if (!documents.isEmpty()) {
                log.info("PDF 段落读取成功: {}, paragraphs={}", fileInfo.getOriginalName(), documents.size());
                return documents;
            }
        } catch (Exception e) {
            log.warn("PDF 段落读取失败，回退到按页读取: {} - {}", fileInfo.getOriginalName(), e.getMessage());
        }
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> documents = reader.get();
        log.info("PDF 按页读取完成: {}, pages={}", fileInfo.getOriginalName(), documents.size());
        return documents;
    }
}
