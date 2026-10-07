package com.gj.llm.netty.spi;

import com.gj.llm.netty.protocol.HandshakeRequest;

/**
 * 握手鉴权 SPI —— gj-netty 编译期不依赖任何鉴权体系（默认拒绝原则），
 * 由使用方提供实现（gj-security 提供基于 JWT 的默认实现 bean）。
 *
 * <p>无实现 bean 时，基座拒绝所有连接（对齐"后端默认拒绝"惯例）。</p>
 *
 * @author gj-llm
 */
public interface HandshakeAuthenticator {

    /**
     * 校验握手请求并返回客户端身份。
     *
     * @return 身份；返回 {@code null} 或抛出异常均视为鉴权失败（连接以 401 关闭并计入失败限流）
     */
    ClientPrincipal authenticate(HandshakeRequest request);
}
