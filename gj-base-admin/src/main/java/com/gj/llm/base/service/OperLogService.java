package com.gj.llm.base.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.OperLogEntity;

import java.time.LocalDateTime;

/**
 * 操作日志服务 -- 审计日志查询与清理。
 *
 * <p>日志写入走 {@code OperLogEventListener} 异步落库，本服务只提供查询与清空。</p>
 *
 * @author gj-llm
 */
public interface OperLogService extends IService<OperLogEntity> {

    /**
     * 分页查询操作日志。
     *
     * @param page     页码（1 起）
     * @param size     每页条数
     * @param module   操作模块（精确匹配，可空）
     * @param operator 操作人用户名（模糊匹配，可空）
     * @param status   操作状态：1=成功 0=失败（可空）
     * @return 分页结果，按操作时间倒序
     */
    IPage<OperLogEntity> page(long page, long size, String module, String operator, Integer status);

    /**
     * 清空全部操作日志。
     */
    void clearAll();

    /**
     * 清理指定时间之前的操作日志（保留期定时清理用，分批执行）。
     *
     * @param threshold 时间阈值，早于该时间的记录被删除
     * @return 清理条数
     */
    int clearBefore(LocalDateTime threshold);
}
