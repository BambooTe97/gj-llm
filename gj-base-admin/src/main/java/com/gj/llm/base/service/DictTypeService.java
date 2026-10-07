package com.gj.llm.base.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.DictTypeEntity;
import com.gj.llm.base.model.DictTypeCreateRequest;
import com.gj.llm.base.model.DictTypeUpdateRequest;

/**
 * 字典类型服务。
 *
 * @author gj-llm
 */
public interface DictTypeService extends IService<DictTypeEntity> {

    /**
     * 分页查询字典类型。
     *
     * @param page     页码（1 起）
     * @param size     每页条数
     * @param keyword  关键字（名称/类型编码模糊匹配，可空）
     * @param status   状态：1=启用 0=停用（可空）
     * @return 分页结果，按创建时间倒序
     */
    IPage<DictTypeEntity> page(long page, long size, String keyword, Integer status);

    /**
     * 创建字典类型（type 编码唯一）。
     *
     * @param request 创建请求
     * @return 创建后的实体
     */
    DictTypeEntity create(DictTypeCreateRequest request);

    /**
     * 更新字典类型；type 编码改名时校验唯一后级联更新字典数据的 dict_type。
     *
     * @param id      类型 ID
     * @param request 更新请求
     * @return 更新后的实体
     */
    DictTypeEntity update(Long id, DictTypeUpdateRequest request);

    /**
     * 删除字典类型；类型下存在字典数据时拒绝。
     *
     * @param id 类型 ID
     */
    void delete(Long id);
}
