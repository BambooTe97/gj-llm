package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.OperLogEntity;
import com.gj.llm.base.mapper.OperLogMapper;
import com.gj.llm.base.service.DataScopeService;
import com.gj.llm.common.util.StringUtils;
import com.gj.llm.base.service.OperLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

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

    /** 清空/清理的分批大小 */
    private static final int BATCH_SIZE = 1000;

    private final DataScopeService dataScopeService;

    public OperLogServiceImpl(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Override
    public IPage<OperLogEntity> page(long page, long size, String module, String operator, Integer status) {
        LambdaQueryWrapper<OperLogEntity> wrapper = new LambdaQueryWrapper<>();
        applyDataScope(wrapper);
        wrapper.eq(StringUtils.isNotBlank(module), OperLogEntity::getModule, module);
        // 条件标志位不短路实参求值：operator 派生值须在判空后构造，否则 operator=null 时 NPE
        if (StringUtils.isNotBlank(operator)) {
            wrapper.like(OperLogEntity::getOperator, StringUtils.escapeLike(operator.trim()));
        }
        wrapper.eq(status != null, OperLogEntity::getStatus, status)
                .orderByDesc(OperLogEntity::getCreatedAt);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public void clearAll() {
        log.info("操作日志已清空: {} 条", deleteInBatches(w -> { }));
    }

    @Override
    public int clearBefore(LocalDateTime threshold) {
        int n = deleteInBatches(w -> w.lt(OperLogEntity::getCreatedAt, threshold));
        log.info("清理过期操作日志（{} 前）: {} 条", threshold, n);
        return n;
    }

    /** 按 id 分批删除（每批 {@value #BATCH_SIZE} 条），避免单条全表 DELETE 的长事务与大锁 */
    private int deleteInBatches(Consumer<LambdaQueryWrapper<OperLogEntity>> condition) {
        long total = 0;
        while (true) {
            LambdaQueryWrapper<OperLogEntity> wrapper = new LambdaQueryWrapper<OperLogEntity>()
                    .select(OperLogEntity::getId)
                    .last("LIMIT " + BATCH_SIZE);
            condition.accept(wrapper);
            List<Object> ids = listObjs(wrapper);
            if (ids.isEmpty()) {
                break;
            }
            removeByIds(ids);
            total += ids.size();
        }
        return (int) total;
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
