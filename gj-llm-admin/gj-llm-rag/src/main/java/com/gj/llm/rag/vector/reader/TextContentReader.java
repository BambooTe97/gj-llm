package com.gj.llm.rag.vector.reader;

import com.gj.llm.file.model.FileInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TextContentReader implements FileContentReader {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "txt", "md", "json", "xml", "csv", "yml", "yaml", "html", "htm",
            "java", "py", "js", "ts", "css", "sql", "properties", "ini", "cfg",
            "log", "sh", "bat", "gradle"
    );

    @Override
    public boolean supports(String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension);
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
