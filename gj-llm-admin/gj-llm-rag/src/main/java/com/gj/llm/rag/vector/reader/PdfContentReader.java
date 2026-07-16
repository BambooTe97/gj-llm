package com.gj.llm.rag.vector.reader;

import com.gj.llm.file.constant.FileTypeEnum;
import com.gj.llm.file.model.FileInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PdfContentReader implements FileContentReader {

    @Override
    public boolean supports(String extension) {
        return FileTypeEnum.PDF.name().equalsIgnoreCase(extension);
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
        // 回退：按页读取（适用于无结构化段落的 PDF）
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> documents = reader.get();
        log.info("PDF 按页读取完成: {}, pages={}", fileInfo.getOriginalName(), documents.size());
        return documents;
    }
}
