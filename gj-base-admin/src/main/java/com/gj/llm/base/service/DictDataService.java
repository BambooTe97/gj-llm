package com.gj.llm.base.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.DictDataEntity;
import com.gj.llm.base.model.DictDataCreateRequest;
import com.gj.llm.base.model.DictDataUpdateRequest;

import java.util.List;

/**
 * 字典数据服务。
 *
 * @author gj-llm
 */
public interface DictDataService extends IService<DictDataEntity> {

    /**
     * 分页查询字典数据（按字典类型）。
     *
     * @param page     页码（1 起）
     * @param size     每页条数
     * @param dictType 字典类型编码（必填）
     * @param keyword  关键字（标签/键值模糊匹配，可空）
     * @return 分页结果，按 sort 升序
     */
    IPage<DictDataEntity> page(long page, long size, String dictType, String keyword);

    /**
     * 查询指定类型的启用字典数据（业务侧消费入口，按 sort 升序）。
     *
     * @param dictType 字典类型编码
     * @return 启用状态的字典数据列表
     */
    List<DictDataEntity> listByType(String dictType);

    /**
     * 创建字典数据（同类型下 dictValue 唯一）。
     *
     * @param request 创建请求
     * @return 创建后的实体
     */
    DictDataEntity create(DictDataCreateRequest request);

    /**
     * 更新字典数据（dictType 不可改，同类型下 dictValue 唯一）。
     *
     * @param id      数据 ID
     * @param request 更新请求
     * @return 更新后的实体
     */
    DictDataEntity update(Long id, DictDataUpdateRequest request);

    /**
     * 删除字典数据。
     *
     * @param id 数据 ID
     */
    void delete(Long id);
}
