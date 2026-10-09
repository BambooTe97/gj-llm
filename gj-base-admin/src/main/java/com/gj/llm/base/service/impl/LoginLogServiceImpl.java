package com.gj.llm.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.base.entity.LogininforEntity;
import com.gj.llm.base.mapper.LogininforMapper;
import com.gj.llm.base.service.DataScopeService;
import com.gj.llm.base.service.LoginLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 登录日志服务实现。
 *
 * <p>分页查询应用角色数据权限（{@link DataScopeService#resolveScopedUserIds}）：全域不过滤；
 * 非全域按 user_id 收窄，user_id 为 NULL 的记录（如账号锁定时用户尚未认证）仅全域可见。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class LoginLogServiceImpl extends ServiceImpl<LogininforMapper, LogininforEntity> implements LoginLogService {

    private final DataScopeService dataScopeService;

    public LoginLogServiceImpl(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Override
    public IPage<LogininforEntity> page(long page, long size, String username, String ip, Integer status) {
        LambdaQueryWrapper<LogininforEntity> wrapper = new LambdaQueryWrapper<>();
        applyDataScope(wrapper);
        wrapper.like(username != null && !username.isBlank(), LogininforEntity::getUsername, username)
                .like(ip != null && !ip.isBlank(), LogininforEntity::getIp, ip)
                .eq(status != null, LogininforEntity::getStatus, status)
                .orderByDesc(LogininforEntity::getLoginTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public void clearAll() {
        remove(null);
        log.info("登录日志已清空");
    }

    /** 数据权限：null=全域不过滤；空集=空域（1=0）；否则 in(user_id, 可见用户) */
    private void applyDataScope(LambdaQueryWrapper<LogininforEntity> wrapper) {
        List<Long> scopedUserIds = dataScopeService.resolveScopedUserIds();
        if (scopedUserIds == null) {
            return;
        }
        if (scopedUserIds.isEmpty()) {
            wrapper.apply("1=0");
            return;
        }
        wrapper.in(LogininforEntity::getUserId, scopedUserIds);
    }
}
