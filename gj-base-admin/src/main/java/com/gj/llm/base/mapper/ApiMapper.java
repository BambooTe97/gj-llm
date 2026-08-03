package com.gj.llm.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gj.llm.base.entity.ApiEntity;

/**
 * 接口 Mapper -- 管理 {@code sys_api} 表。
 *
 * <p>简单 CRUD 由 {@link BaseMapper} 提供；扫描入库、缓存构建等逻辑在 Service/Scanner 中
 * 以 {@code LambdaQueryWrapper} 完成。</p>
 *
 * @author gj-llm
 */
public interface ApiMapper extends BaseMapper<ApiEntity> {
}
