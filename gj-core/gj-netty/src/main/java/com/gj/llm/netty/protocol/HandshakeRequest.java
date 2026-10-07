package com.gj.llm.netty.protocol;

import java.util.Map;

/**
 * 握手请求抽象 —— 鉴权 SPI 的入参，屏蔽 Netty HTTP 细节，
 * 使 {@code com.gj.llm.netty.spi.HandshakeAuthenticator} 的实现（如 gj-security 的 JWT 实现）不依赖 Netty。
 *
 * @param path       请求路径（不含查询串）
 * @param queryParams 查询参数（浏览器 WS 无法自定义请求头，token 常走查询参数）
 * @param headers    请求头（key 统一小写）
 * @param remoteIp   客户端来源 IP（注意：经反代且未配 Proxy Protocol 时为代理 IP）
 * @author gj-llm
 */
public record HandshakeRequest(String path,
                               Map<String, String> queryParams,
                               Map<String, String> headers) {

    /** 便捷取头（不区分大小写的 key 已在构建时归一） */
    public String header(String name) {
        return headers.get(name.toLowerCase());
    }

    /** 便捷取查询参数 */
    public String queryParam(String name) {
        return queryParams.get(name);
    }
}
