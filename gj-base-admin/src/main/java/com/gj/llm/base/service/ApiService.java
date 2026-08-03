package com.gj.llm.base.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.ApiEntity;

import java.util.List;

/**
 * 接口服务接口 -- 管理 {@code sys_api} 接口元数据，供 Controller 与启动组件调用。
 *
 * @author gj-llm
 */
public interface ApiService extends IService<ApiEntity> {

    /** 查询全部有效接口（is_deleted=0） */
    List<ApiEntity> listActive();

    /** 按 controller 全限定类名 + 方法名查询接口（含已删除记录，供扫描 upsert） */
    ApiEntity findByControllerMethod(String controller, String methodName);
}
