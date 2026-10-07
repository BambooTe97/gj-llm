package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.OperLogEntity;
import com.gj.llm.base.mapper.OperLogMapper;
import com.gj.llm.base.service.OperLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务实现。
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class OperLogServiceImpl extends ServiceImpl<OperLogMapper, OperLogEntity> implements OperLogService {

    @Override
    public IPage<OperLogEntity> page(long page, long size, String module, String operator, Integer status) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<OperLogEntity>()
                .eq(module != null && !module.isBlank(), OperLogEntity::getModule, module)
                .like(operator != null && !operator.isBlank(), OperLogEntity::getOperator, operator)
                .eq(status != null, OperLogEntity::getStatus, status)
                .orderByDesc(OperLogEntity::getCreatedAt));
    }

    @Override
    public void clearAll() {
        remove(null);
        log.info("操作日志已清空");
    }
}
