package com.gj.llm.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.rag.entity.DatasetFileEntity;
import com.gj.llm.rag.model.DatasetFileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DatasetFileService extends IService<DatasetFileEntity> {

    IPage<DatasetFileVO> pageByDataset(Long datasetId, int page, int pageSize);

    DatasetFileEntity upload(Long datasetId, MultipartFile file);

    void delete(Long datasetFileId);

    void reparse(Long datasetFileId);

    void processDatasetFile(Long dfId);
}
