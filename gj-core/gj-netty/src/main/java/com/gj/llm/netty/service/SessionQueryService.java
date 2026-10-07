package com.gj.llm.netty.service;

import com.gj.llm.netty.session.ClientSession;

import java.util.List;

/**
 * 会话查询门面 —— 在线状态查询（在线判定、按身份找会话、总量观测）。
 *
 * @author gj-llm
 */
public interface SessionQueryService {

    /** 该身份是否在线（本节点有活跃连接） */
    boolean isOnline(String principalId);

    /** 按身份查活跃会话（无则空列表） */
    List<ClientSession> findByPrincipal(String principalId);

    /** 本节点活跃连接总数 */
    int activeCount();
}
