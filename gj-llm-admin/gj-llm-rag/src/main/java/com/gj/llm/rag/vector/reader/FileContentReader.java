package com.gj.llm.rag.vector.reader;

import com.gj.llm.file.model.FileInfo;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * 文件内容读取策略接口 —— 不同文件类型对应不同的读取实现。
 *
 * <p>Spring 容器中所有 FileContentReader Bean 会被自动注入为 List，
 * 调用方按 extension 逐个匹配，取首个 supports() 返回 true 的实现。</p>
 *
 * @author zf
 */
public interface FileContentReader {

    /**
     * 判断是否支持该扩展名
     * @param extension 文件扩展名（小写，不含点号）
     */
    boolean supports(String extension);

    /**
     * 读取文件内容为 Document 列表
     * @param resource 文件资源
     * @param fileInfo 文件元信息
     */
    List<Document> read(Resource resource, FileInfo fileInfo);
}
