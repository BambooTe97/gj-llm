package com.gj.llm.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.agent.entity.VectorModelEntity;
import com.gj.llm.agent.model.VectorModelCreateRequest;
import com.gj.llm.agent.model.VectorModelUpdateRequest;

import java.util.List;

public interface VectorModelService extends IService<VectorModelEntity> {

    IPage<VectorModelEntity> page(int page, int pageSize);

    List<VectorModelEntity> listAll();

    VectorModelEntity getByTypeCode(String typeCode);

    VectorModelEntity create(VectorModelCreateRequest request);

    VectorModelEntity update(Long id, VectorModelUpdateRequest request);

    void delete(Long id);
}
