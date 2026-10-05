package com.gj.llm.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gj.llm.auth.entity.ResourceAclEntity;

/**
 * 资源授权 Mapper —— 基础 CRUD 走 MyBatis-Plus 通用方法（mapper 扫描由
 * {@code MyBatisGlobalConfig} 的 {@code com.gj.llm.**.mapper} 覆盖，无需注解）。
 *
 * @author gj-llm
 */
public interface ResourceAclMapper extends BaseMapper<ResourceAclEntity> {
}
