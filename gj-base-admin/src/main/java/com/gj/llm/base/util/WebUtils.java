package com.gj.llm.base.util;

import com.gj.llm.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 请求工具类 -- 客户端 IP 等请求上下文提取。
 *
 * <h3>信任代理配置</h3>
 * <p>X-Forwarded-For / X-Real-IP 头默认不受信任（直连部署时客户端可伪造，
 * 污染审计与限流依据）；仅当部署在可信反向代理之后时，
 * 由 {@code gj.llm.web.trust-xff=true}（{@code WebProperties}）开启解析。</p>
 *
 * @author gj-llm
 */
public final class WebUtils {

    private WebUtils() {
    }

    /**
     * 获取客户端 IP（不信任代理头，直接取 remoteAddr）。
     */
    public static String getClientIp(HttpServletRequest request) {
        return getClientIp(request, false);
    }

    /**
     * 获取客户端真实 IP。
     *
     * <p>{@code trustXff=true} 时优先级：X-Forwarded-For 首段（多层代理场景）→ X-Real-IP → remoteAddr；
     * 否则只取 remoteAddr。</p>
     *
     * @param request  当前请求
     * @param trustXff 是否信任 X-Forwarded-For / X-Real-IP（仅在可信反向代理之后开启）
     * @return 客户端 IP；request 为 null 或无法识别时返回空串
     */
    public static String getClientIp(HttpServletRequest request, boolean trustXff) {
        if (request == null) {
            return "";
        }
        if (trustXff) {
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
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    private static boolean isValidIp(String ip) {
        return StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip.trim());
    }
}
