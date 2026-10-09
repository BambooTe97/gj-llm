package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.OperLogEntity;
import com.gj.llm.base.mapper.OperLogMapper;
import com.gj.llm.base.service.DataScopeService;
import com.gj.llm.base.service.OperLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 操作日志服务实现。
 *
 * <p>分页查询应用角色数据权限（{@link DataScopeService#resolveScopedUserIds}）：全域不过滤；
 * 非全域按 user_id 收窄，user_id 为 NULL 的记录（如登录前失败）仅全域（管理员）可见。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class OperLogServiceImpl extends ServiceImpl<OperLogMapper, OperLogEntity> implements OperLogService {

    private final DataScopeService dataScopeService;

    public OperLogServiceImpl(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Override
    public IPage<OperLogEntity> page(long page, long size, String module, String operator, Integer status) {
        LambdaQueryWrapper<OperLogEntity> wrapper = new LambdaQueryWrapper<>();
        applyDataScope(wrapper);
        wrapper.eq(module != null && !module.isBlank(), OperLogEntity::getModule, module)
                .like(operator != null && !operator.isBlank(), OperLogEntity::getOperator, operator)
                .eq(status != null, OperLogEntity::getStatus, status)
                .orderByDesc(OperLogEntity::getCreatedAt);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public void clearAll() {
        remove(null);
        log.info("操作日志已清空");
    }

    /** 数据权限：null=全域不过滤；空集=空域（1=0）；否则 in(user_id, 可见用户) */
    private void applyDataScope(LambdaQueryWrapper<OperLogEntity> wrapper) {
        List<Long> scopedUserIds = dataScopeService.resolveScopedUserIds();
        if (scopedUserIds == null) {
            return;
        }
        if (scopedUserIds.isEmpty()) {
            wrapper.apply("1=0");
            return;
        }
        wrapper.in(OperLogEntity::getUserId, scopedUserIds);
    }
}
