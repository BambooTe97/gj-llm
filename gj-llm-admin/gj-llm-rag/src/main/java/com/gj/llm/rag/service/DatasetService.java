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

    /**
     * 原子调整知识库计数（数据库端自增/自减，避免并发"读-改-写"丢失更新）。
     * 两个增量同时为 0 时不执行更新。
     *
     * @param id           知识库 ID
     * @param docDelta     文档数增量（0=不变，负数=递减）
     * @param segmentDelta 切片数增量（0=不变，负数=递减）
     */
    void adjustCounters(Long id, int docDelta, int segmentDelta);

    void delete(Long id);
}
