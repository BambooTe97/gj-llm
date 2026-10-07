package com.gj.llm.base.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.SysConfigEntity;
import com.gj.llm.base.model.SysConfigCreateRequest;
import com.gj.llm.base.model.SysConfigUpdateRequest;

/**
 * 参数配置服务 -- 运行时可调参数的增删改查。
 *
 * @author gj-llm
 */
public interface SysConfigService extends IService<SysConfigEntity> {

    /**
     * 分页查询参数配置。
     *
     * @param page    页码（1 起）
     * @param size    每页条数
     * @param keyword 关键字（名称/键名模糊匹配，可空）
     * @return 分页结果，按创建时间倒序
     */
    IPage<SysConfigEntity> page(long page, long size, String keyword);

    /**
     * 按键名查询参数（业务侧消费入口）。
     *
     * @param key 参数键名
     * @return 参数实体；不存在时返回 null
     */
    SysConfigEntity getByKey(String key);

    /**
     * 创建参数（configKey 全局唯一）。
     *
     * @param request 创建请求
     * @return 创建后的实体
     */
    SysConfigEntity create(SysConfigCreateRequest request);

    /**
     * 更新参数（configKey 不可修改；内置参数可改值不可删除）。
     *
     * @param id      参数 ID
     * @param request 更新请求
     * @return 更新后的实体
     */
    SysConfigEntity update(Long id, SysConfigUpdateRequest request);

    /**
     * 删除参数；内置参数（builtIn=1）拒绝删除。
     *
     * @param id 参数 ID
     */
    void delete(Long id);
}
