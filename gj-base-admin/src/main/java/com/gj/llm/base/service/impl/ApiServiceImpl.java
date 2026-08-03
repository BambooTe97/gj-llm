package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.ApiEntity;
import com.gj.llm.base.mapper.ApiMapper;
import com.gj.llm.base.service.ApiService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 接口服务实现 -- 通过 {@link ApiMapper} 管理 {@code sys_api}。
 *
 * @author gj-llm
 */
@Service
public class ApiServiceImpl extends ServiceImpl<ApiMapper, ApiEntity> implements ApiService {

    @Override
    public List<ApiEntity> listActive() {
        return list(new LambdaQueryWrapper<ApiEntity>().eq(ApiEntity::getIsDeleted, 0).orderByAsc(ApiEntity::getPath));
    }

    @Override
    public ApiEntity findByControllerMethod(String controller, String methodName) {
        return getOne(new LambdaQueryWrapper<ApiEntity>()
                .eq(ApiEntity::getController, controller)
                .eq(ApiEntity::getMethodName, methodName)
                .last("LIMIT 1"));
    }
}
