package com.gj.llm.base.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 请求工具类 -- 客户端 IP 等请求上下文提取。
 *
 * @author gj-llm
 */
public final class WebUtils {

    private WebUtils() {
    }

    /**
     * 获取客户端真实 IP。
     *
     * <p>优先级：X-Forwarded-For 首段（多层代理场景）→ X-Real-IP → remoteAddr。</p>
     *
     * @param request 当前请求
     * @return 客户端 IP；request 为 null 或无法识别时返回空串
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (isValidIp(ip)) {
            // 多级代理时为逗号分隔链路，取第一个（最初的客户端）
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip.trim());
    }
}
