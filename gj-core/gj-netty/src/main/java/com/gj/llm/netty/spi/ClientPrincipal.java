package com.gj.llm.netty.spi;

import java.util.Map;

/**
 * 客户端身份抽象 —— 不绑定"用户"概念：可以是用户、设备、租户、agent 等任何接入方。
 * 由 {@link HandshakeAuthenticator} 在握手阶段产出，挂到连接上贯穿生命周期。
 *
 * <p>孵化期接口（{@code @Experimental} 语义，暂不以注解形式落地），演进规则见
 * {@code GJ_NETTY_GUIDE.md} 第十三节。</p>
 *
 * @param id         身份唯一标识（如用户 ID）
 * @param type       身份类型（如 "user"、"agent"、"device"）
 * @param attributes 附加属性（如 username、tenantId）
 * @author gj-llm
 */
public record ClientPrincipal(String id, String type, Map<String, String> attributes) {

    public ClientPrincipal {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
