package com.gj.llm.rag.vector.reader;

import com.gj.llm.file.model.FileInfo;
import com.gj.llm.rag.constant.FileReaderCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Markdown 文件内容读取器 —— 基于 commonmark 解析 AST，按标题层级切分为多个 Document。
 *
 * <p>切分规则：
 * <ul>
 *   <li>每个标题（h1~h6）触发一次文档边界</li>
 *   <li>水平分割线（---）触发文档边界（{@code horizontalRuleCreateDocument=true}）</li>
 *   <li>代码块和引用块各自独立为一个 Document</li>
 * </ul>
 *
 * <p>每个 Document 会携带 metadata：category（header_N / code_block / blockquote）、
 * lang（围栏代码块的语言标识）、title（所属标题文本）。</p>
 *
 * @author zf
 */
@Slf4j
@Component
public class MarkdownContentReader implements FileContentReader {

    private static final MarkdownDocumentReaderConfig CONFIG = MarkdownDocumentReaderConfig.builder()
            .withHorizontalRuleCreateDocument(true)
            .withIncludeCodeBlock(false)
            .withIncludeBlockquote(false)
            .build();

    @Override
    public boolean supports(String extension) {
        return FileReaderCategory.MARKDOWN.supports(extension);
    }

    @Override
    public List<Document> read(Resource resource, FileInfo fileInfo) {
        try {
            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, CONFIG);
            List<Document> documents = reader.get();
            log.info("Markdown 读取成功: {}, sections={}", fileInfo.getOriginalName(), documents.size());
            return documents;
        } catch (Exception e) {
            log.error("Markdown 读取失败: {} - {}", fileInfo.getOriginalName(), e.getMessage());
            return List.of();
        }
    }
}
