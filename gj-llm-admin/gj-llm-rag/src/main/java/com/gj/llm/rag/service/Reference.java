package com.gj.llm.rag.service;

/**
 * 引用片段 -- 检索命中的单条参考来源,用于对话层做引用溯源展示。
 *
 * <p>每条引用自带所属知识库标识(而非依赖会话级单一知识库),
 * 为后续"智能路由/多知识库并发检索"预留:届时检索编排改为多库扇出,
 * 此结构与前端渲染无需调整。</p>
 *
 * @param rank           排名(从 1 开始,与 LLM 上下文中的【片段n】编号一一对应)
 * @param content        片段正文(取父块内容,截断前 200 字,与模型实际所见一致)
 * @param score          rerank 精排分数(保留 3 位小数)
 * @param source         来源文件名
 * @param datasetName    所属知识库名称(单库检索时为常量,多库检索时逐条区分)
 * @param datasetId      所属知识库 ID
 * @param datasetFileId  所属知识库文件 ID
 * @author gj-llm
 */
public record Reference(int rank, String content, double score, String source,
                        String datasetName, Long datasetId, Object datasetFileId) {
}
