package com.gj.llm.rag.listener;

import com.gj.llm.rag.event.DatasetFileUploadedEvent;
import com.gj.llm.rag.service.DatasetFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatasetFileEventListener {

    private final DatasetFileService datasetFileService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDatasetFileUploaded(DatasetFileUploadedEvent event) {
        log.info("收到文件上传事件，开始异步向量化: dfId={}", event.dfId());
        datasetFileService.processDatasetFile(event.dfId());
    }
}
