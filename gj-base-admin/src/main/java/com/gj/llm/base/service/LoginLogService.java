package com.gj.llm.base.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.LogininforEntity;

/**
 * 登录日志服务 -- 查询与清空（管理端）。
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

    /** 清空全部登录日志 */
    void clearAll();
}
