package com.gj.llm.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * chat 编排配置 -- 智能体声明 + 路由规则。
 *
 * <p>体现 chat 的"丰富可扩展":内部智能体(internal)用 Java 实现 + 这里声明模型;
 * 外部/市面智能体(remote)在此声明 endpoint 即可接入;路由规则决定走哪个智能体。
 * 加智能体只改此配置(或加一个 bean),不改编排主干。</p>
 *
 * @author gj-llm
 */
@Data
@Component
@ConfigurationProperties(prefix = "gj.llm.chat")
public class ChatProperties {

    /** 已声明的智能体,id -> 配置 */
    private Map<String, AgentConfig> agents = new LinkedHashMap<>();

    /** 路由配置 */
    private Routing routing = new Routing();

    /** 单个智能体配置 */
    @Data
    public static class AgentConfig {

        /** 类型:internal(Java 实现)/ remote(外部 HTTP API) */
        private String type = "internal";

        /** 调用的模型名(internal 用,如 gemma2:2b) */
        private String model;

        /** 是否启用深度思考 */
        private boolean thinking = true;

        /** 生成 token 上限(num_predict) */
        private Integer numPredict;

        // ===== remote 专用 =====

        /** 外部智能体接口地址 */
        private String endpoint;

        /** 鉴权头(如 "Bearer xxx") */
        private String authHeader;
    }

    /** 路由配置 */
    @Data
    public static class Routing {

        /** 默认智能体(无规则命中时) */
        private String defaultAgent = "chitchat";

        /** 路由规则(按顺序匹配,首个命中生效) */
        private List<Rule> rules = new ArrayList<>();
    }

    /** 单条路由规则 */
    @Data
    public static class Rule {

        /** 命中时使用的智能体 id */
        private String agent;

        /** 条件:是否有数据集(datasetId != null) */
        private boolean hasDataset;
    }
}
