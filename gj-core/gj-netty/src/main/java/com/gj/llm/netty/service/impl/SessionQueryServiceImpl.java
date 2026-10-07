package com.gj.llm.netty.service.impl;

import com.gj.llm.netty.service.SessionQueryService;
import com.gj.llm.netty.session.ClientSession;
import com.gj.llm.netty.session.SessionRegistry;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 会话查询门面默认实现。由 {@code GjNettyConfig} 装配。
 *
 * @author gj-llm
 */
@RequiredArgsConstructor
public class SessionQueryServiceImpl implements SessionQueryService {

    private final SessionRegistry sessionRegistry;

    @Override
    public boolean isOnline(String principalId) {
        return !sessionRegistry.findByPrincipal(principalId).isEmpty();
    }

    @Override
    public List<ClientSession> findByPrincipal(String principalId) {
        return sessionRegistry.findByPrincipal(principalId);
    }

    @Override
    public int activeCount() {
        return sessionRegistry.activeCount();
    }
}
