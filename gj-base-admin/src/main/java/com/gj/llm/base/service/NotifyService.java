package com.gj.llm.base.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gj.llm.base.entity.NotifyEntity;
import com.gj.llm.base.model.NotifyAdminVO;
import com.gj.llm.base.model.NotifyVO;

import java.util.List;

/**
 * 通知服务 —— 通知的落库、查询、已读管理与实时推送编排。
 *
 * <p>推送语义：<b>先落库、后推送</b>。WebSocket 推送只是实时提醒，
 * 用户离线时由 REST 列表兜底（上线拉取即补齐），基座的
 * {@code OfflineMessagePolicy} 仅作观测记录。</p>
 *
 * @author gj-llm
 */
public interface NotifyService extends IService<NotifyEntity> {

    // ==================== topic 常量（协议契约，前后端与基座共用） ====================

    /** 下行：服务端 → 客户端，新通知实时提醒 */
    String TOPIC_MESSAGE = "notify.message";

    /** 上行：客户端 → 服务端，已读回执（payload: {"ids": ["1","2"]}） */
    String TOPIC_READ = "notify.read";

    // ==================== 查询 ====================

    /** 当前用户的通知列表（最近 50 条，新的在前） */
    List<NotifyVO> listByUser();

    /** 当前用户的未读数 */
    long unreadCount();

    // ==================== 已读管理 ====================

    /**
     * 标记单条已读（HTTP 通道，按当前登录用户校验归属）。
     *
     * @return 是否有记录被更新（false = 不存在或不属于当前用户）
     */
    boolean markRead(Long id);

    /**
     * 全部标记已读（HTTP 通道）。
     *
     * @return 更新的条数
     */
    int markAllRead();

    /**
     * 批量标记已读（WS 上行通道，按连接身份校验归属 —— 监听器运行在派发线程，
     * 无 SecurityContext，归属以 {@code principalId} 为准）。
     *
     * @return 更新的条数
     */
    int markReadByPrincipal(String principalId, List<Long> ids);

    // ==================== 创建与推送 ====================

    /**
     * 创建通知并尝试实时推送（先落库后推送）。
     *
     * @param targetUserId 接收用户 ID（须存在）
     * @param title        标题
     * @param content      内容
     * @param level        级别：info / success / warning / error
     * @return 已落库的通知（含 ID）
     */
    NotifyVO createAndPush(Long targetUserId, String title, String content, String level);

    // ==================== 管理端（通知管理中心） ====================

    /**
     * 分页查询全部通知（管理视角，含接收人用户名解析）。
     *
     * @param keyword  标题/内容模糊匹配（可空）
     * @param level    级别过滤（可空）
     * @param readFlag 已读过滤（可空）
     */
    IPage<NotifyAdminVO> pageForAdmin(long page, long size, String keyword, String level, Integer readFlag);
}
