package com.gj.llm.rag.event;

/**
 * 文件上传完成并已入库后发布的事件。
 * 监听方应在事务提交后再处理向量化，确保能读到已提交的 dataset_file 记录。
 */
public record DatasetFileUploadedEvent(Long dfId) {
}
