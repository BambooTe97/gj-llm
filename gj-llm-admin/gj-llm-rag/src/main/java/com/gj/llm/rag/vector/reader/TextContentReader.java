package com.gj.llm.rag.vector.reader;

import com.gj.llm.file.model.FileInfo;
import com.gj.llm.rag.constant.FileReaderCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 纯文本文件内容读取器 —— 按 UTF-8 读取整个文件为单个 Document。
 *
 * <p>覆盖常见文本格式：txt/json/xml/csv/yml/yaml、主流编程语言源码、
 * 配置文件（properties/ini/cfg/gradle）、日志、Shell/Batch 脚本等。</p>
 *
 * <p>不做结构解析，全文作为一个 Document 返回，后续由 RecursiveCharacterTextSplitter 切分。
 * Markdown 和 HTML 不在此 Reader 范围内，分别由 MarkdownContentReader / TikaContentReader 处理。</p>
 *
 * @author zf
 */
@Slf4j
@Component
public class TextContentReader implements FileContentReader {

    @Override
    public boolean supports(String extension) {
        return FileReaderCategory.TEXT.supports(extension);
    }

    @Override
    public List<Document> read(Resource resource, FileInfo fileInfo) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String content = br.lines().collect(Collectors.joining("\n"));
            if (!content.isBlank()) {
                return List.of(new Document(content));
            }
        } catch (Exception e) {
            log.info("读取文本文件失败: {} - {}", fileInfo.getOriginalName(), e.getMessage());
        }
        return List.of();
    }
}
