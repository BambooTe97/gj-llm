package com.gj.llm.agent.vector.reader;

import com.gj.llm.file.model.FileInfo;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * 文件内容读取策略接口 —— 不同文件后缀对应不同的读取实现。
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
