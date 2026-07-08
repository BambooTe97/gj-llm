package com.gj.llm.agent.vector.reader;

import com.gj.llm.file.constant.FileTypeEnum;
import com.gj.llm.file.model.FileInfo;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PdfContentReader implements FileContentReader {

    @Override
    public boolean supports(String extension) {
        return FileTypeEnum.PDF.name().equalsIgnoreCase(extension);
    }

    @Override
    public List<Document> read(Resource resource, FileInfo fileInfo) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        return reader.get();
    }
}
