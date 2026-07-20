package com.gj.llm.rag.vector.reader;

import com.gj.llm.file.model.FileInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件读取分发器 —— 根据扩展名将文件分发给匹配的 {@link FileContentReader}。
 *
 * @author zf
 */
@Slf4j
@Component
public class FileReaderDispatcher {

    private final List<FileContentReader> readers;

    public FileReaderDispatcher(List<FileContentReader> readers) {
        this.readers = readers;
    }

    /**
     * 根据文件扩展名匹配 Reader 并读取为 Document 列表。
     *
     * @param resource 文件资源
     * @param fileInfo 文件元信息（用于获取扩展名）
     * @return 匹配 Reader 读取的 Document 列表；无匹配时返回空列表
     */
    public List<Document> read(Resource resource, FileInfo fileInfo) {
        String extension = fileInfo.getExtension() != null ? fileInfo.getExtension().toLowerCase() : "";
        for (FileContentReader reader : readers) {
            if (reader.supports(extension)) {
                return reader.read(resource, fileInfo);
            }
        }
        log.debug("不支持的文件类型: .{} ({})", extension, fileInfo.getOriginalName());
        return List.of();
    }
}
