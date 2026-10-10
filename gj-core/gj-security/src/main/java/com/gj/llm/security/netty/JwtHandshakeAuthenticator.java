package com.gj.llm.security.netty;

import com.gj.llm.common.util.StringUtils;
import com.gj.llm.netty.protocol.HandshakeRequest;
import com.gj.llm.netty.spi.ClientPrincipal;
import com.gj.llm.netty.spi.HandshakeAuthenticator;
import com.gj.llm.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * WebSocket 握手 JWT 鉴权器 —— gj-security 向 gj-netty 的 {@code HandshakeAuthenticator}
 * SPI 提供的默认实现（依赖方向：gj-security → gj-netty，gj-netty 不认识 JWT）。
 *
 * <p>令牌来源优先级：
 * <ol>
 *   <li>{@code Authorization: Bearer <token>} 请求头（非浏览器客户端推荐）</li>
 *   <li>{@code ?token=<token>} 查询参数（浏览器 WebSocket API 无法自定义请求头时的兜底；
 *       有日志泄露风险，接入文档须明示，建议配合短时效 token）</li>
 * </ol>
 * 仅接受 Access Token（复用 {@link JwtUtils#validateAccessToken}，含签名/过期/类型校验）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeAuthenticator implements HandshakeAuthenticator {

    private static final String BEARER_PREFIX = "bearer ";

    private final JwtUtils jwtUtils;

    @Override
    public ClientPrincipal authenticate(HandshakeRequest request) {
        String token = resolveToken(request);
        if (StringUtils.isBlank(token)) {
            return null;
        }
        if (!jwtUtils.validateAccessToken(token)) {
            return null;
        }
        Long userId = jwtUtils.getUserId(token);
        if (userId == null) {
            return null;
        }
        String username = jwtUtils.getUsername(token);
        return new ClientPrincipal(String.valueOf(userId), "user",
                Map.of("username", username == null ? "" : username));
    }

    private String resolveToken(HandshakeRequest request) {
        String authorization = request.header("Authorization");
        if (authorization != null && authorization.toLowerCase().startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return request.queryParam("token");
    }
}
