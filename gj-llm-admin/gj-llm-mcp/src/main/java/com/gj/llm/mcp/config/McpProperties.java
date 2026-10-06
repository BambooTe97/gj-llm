package com.gj.llm.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP 模块配置（gj.llm.mcp.*）。
 *
 * <p>server 节为平台侧总开关（与 starter 的 spring.ai.mcp.server.enabled 双闸）；
 * client 节为外部 MCP 连接参数；crypto.secret 为凭据加密密钥，生产必须配置。</p>
 *
 * @author gj-llm
 */
@Data
@Component
@ConfigurationProperties(prefix = "gj.llm.mcp")
public class McpProperties {

    /** MCP Server 端（对外暴露知识库工具） */
    private Server server = new Server();

    /** MCP Client 端（接入外部工具 server） */
    private Client client = new Client();

    /** 凭据加密 */
    private Crypto crypto = new Crypto();

    /** 审计 */
    private Audit audit = new Audit();

    @Data
    public static class Server {
        /** 平台侧总开关：false 时不注册 MCP Server 工具 Provider（与 starter 开关双闸） */
        private boolean enabled = true;
        /** 对外暴露的服务名 */
        private String name = "gj-llm-knowledge";
    }

    @Data
    public static class Client {
        /** 全局外部工具总开关（一键止血：false 时 chat 工具循环只保留内置工具） */
        private boolean enabled = true;
        /** 外部工具调用超时（毫秒），落到 MCP 客户端 requestTimeout */
        private long requestTimeoutMs = 30000;
        /** 健康检查间隔（毫秒） */
        private long healthCheckIntervalMs = 60000;
        /** 初始化连接超时（毫秒） */
        private long initTimeoutMs = 10000;
    }

    @Data
    public static class Crypto {
        /** AES-GCM 密钥（Base64 编码 32 字节）；空=开发默认密钥+WARN，生产必须配置 */
        private String secret = "";
    }

    @Data
    public static class Audit {
        /** 入参摘要最大长度（超长截断） */
        private int paramsMaxLength = 512;
    }
}
