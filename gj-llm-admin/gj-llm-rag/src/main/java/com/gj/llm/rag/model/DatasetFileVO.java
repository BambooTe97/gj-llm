package com.gj.llm.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetFileVO {

    private Long id;
    private Long datasetId;
    private Long fileId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileUrl;
    private String status;
    private String errorMessage;
    private Integer segmentCount;
    private Integer progressPercent;
    private String currentStep;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
