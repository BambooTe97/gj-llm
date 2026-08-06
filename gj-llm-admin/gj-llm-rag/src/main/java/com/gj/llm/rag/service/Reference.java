package com.gj.llm.rag.service;

/**
 * 引用片段 -- 检索命中的单条参考来源,用于对话层做引用溯源展示。
 *
 * @param rank           排名(从 1 开始)
 * @param content        片段正文(截断前 200 字)
 * @param score          rerank 精排分数(保留 3 位小数)
 * @param source         来源文件名
 * @param datasetFileId  所属知识库文件 ID
 * @author gj-llm
 */
public record Reference(int rank, String content, double score, String source, Object datasetFileId) {
}
