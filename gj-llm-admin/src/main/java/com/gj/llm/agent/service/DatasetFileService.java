package com.gj.llm.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.agent.entity.DatasetFileEntity;
import com.gj.llm.agent.model.DatasetFileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface DatasetFileService extends IService<DatasetFileEntity> {

    IPage<DatasetFileVO> pageByDataset(Long datasetId, int page, int pageSize);

    DatasetFileEntity upload(Long datasetId, MultipartFile file);

    void delete(Long datasetFileId);

    void reparse(Long datasetFileId);

    List<Map<String, Object>> testSearch(Long datasetId, String query, int topK);

    void processDatasetFile(Long dfId);
}
