package com.gj.llm.base.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.LogininforEntity;

import java.time.LocalDateTime;

/**
 * 登录日志服务 -- 登录审计查询与保留期清理（管理端）。
 *
 * <p>删除只走 {@code LogCleanJob} 保留期定时任务，不提供手动一键清空。</p>
 *
 * @author gj-llm
 */
public interface LoginLogService extends IService<LogininforEntity> {

    /**
     * 分页查询登录日志。
     *
     * @param page     页码
     * @param size     每页条数
     * @param username 登录账号（模糊，可空）
     * @param ip       客户端 IP（模糊，可空）
     * @param status   登录状态：1=成功 0=失败（可空）
     * @return 分页结果，按登录时间倒序
     */
    IPage<LogininforEntity> page(long page, long size, String username, String ip, Integer status);

    /**
     * 清理指定时间之前的登录日志（保留期定时清理用，分批执行）。
     *
     * @param threshold 时间阈值，早于该时间的记录被删除
     * @return 清理条数
     */
    int clearBefore(LocalDateTime threshold);
}
