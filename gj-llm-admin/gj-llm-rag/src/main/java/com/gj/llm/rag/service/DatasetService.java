package com.gj.llm.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.model.DatasetCreateRequest;
import com.gj.llm.rag.model.DatasetUpdateRequest;

import java.util.List;

public interface DatasetService extends IService<DatasetEntity> {

    IPage<DatasetEntity> page(int page, int pageSize);

    List<DatasetEntity> listAll();

    DatasetEntity create(DatasetCreateRequest request);

    DatasetEntity update(Long id, DatasetUpdateRequest request);

    void delete(Long id);
}
