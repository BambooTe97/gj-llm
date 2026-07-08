package com.gj.llm.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gj.llm.agent.entity.VectorModelEntity;
import com.gj.llm.agent.model.VectorModelCreateRequest;
import com.gj.llm.agent.model.VectorModelUpdateRequest;
import com.gj.llm.agent.service.VectorModelService;
import com.gj.llm.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vector-models")
@RequiredArgsConstructor
public class VectorModelController {

    private final VectorModelService vectorModelService;

    @GetMapping
    public ApiResponse<IPage<VectorModelEntity>> list(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(vectorModelService.page(page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<VectorModelEntity> get(@PathVariable Long id) {
        return ApiResponse.ok(vectorModelService.getById(id));
    }

    @PostMapping
    public ApiResponse<VectorModelEntity> create(@Valid @RequestBody VectorModelCreateRequest request) {
        return ApiResponse.ok(vectorModelService.create(request), "向量模型库创建成功");
    }

    @PutMapping("/{id}")
    public ApiResponse<VectorModelEntity> update(@PathVariable Long id,
                                                  @Valid @RequestBody VectorModelUpdateRequest request) {
        return ApiResponse.ok(vectorModelService.update(id, request), "向量模型库更新成功");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        vectorModelService.delete(id);
        return ApiResponse.ok(null, "向量模型库删除成功");
    }
}
