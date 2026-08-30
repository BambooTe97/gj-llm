package com.gj.llm.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 切分与检索相关可调参数。
 *
 * @author zf
 */
@Data
@Component
@ConfigurationProperties(prefix = "gj.llm.rag")
public class RagProperties {

    /** 父块大小 = 子块大小 × 此倍数（父子召回的父粒度）。 */
    private int parentSizeMultiplier = 3;

    /** 确定性上下文前缀（[文档: source > title]）开关，默认开。 */
    private boolean contextPrefixEnabled = true;

    /** LLM 上下文总字符预算，防止父块拼接后溢出 gemma2:2b 的 8k token 窗口。 */
    private int contextBudgetChars = 3500;

    /** 可选：LLM Contextual Retrieval（入库时调 LLM 生成上下文），默认关，需评测验证有正收益再开。 */
    private boolean contextualRetrievalEnabled = false;

    /** 查询改写使用的模型名（rag 自配，独立于 chat 对话模型）；为空时用默认 ChatModel。 */
    private String rewriteModel;

    /** 智能路由（QueryPlanner）配置 */
    private Routing routing = new Routing();

    /** 稠密检索(dense)来源配置 */
    private Dense dense = new Dense();

    /** 智能路由配置（库清单缓存 + 规划模式切换） */
    @Data
    public static class Routing {

        /** READY 库数 ≤ 此值走全库多路召回（忽略 LLM 选库，仅判意图，零路由风险）；超过则启用 LLM 选库 */
        private int fanoutThreshold = 8;

        /** LLM 选库模式下每问最多路由的知识库数 */
        private int maxDatasets = 2;

        /** 规划调用超时（ms），超时走降级链（小库全量扇出 / 大库按文档量取前 N） */
        private long plannerTimeoutMs = 8000;

        /** 库清单 Redis 缓存 TTL（秒）；库增删改时主动失效 */
        private long datasetCacheTtlSeconds = 60;

        /** 规划模型名（空则复用 rewrite-model） */
        private String plannerModel;
    }

    @Data
    public static class Dense {
        /**
         * dense 检索提供者：
         * <ul>
         *   <li>milvus - 向量走 Milvus(专业向量库,省 ES 内存),ES 只做 BM25</li>
         *   <li>es - 向量走 ES KNN(ES 同时做 BM25 + KNN)</li>
         * </ul>
         * 切换时需重建向量库。默认 milvus。
         */
        private String provider = "milvus";

        public boolean isMilvus() {
            return "milvus".equalsIgnoreCase(provider);
        }

        public boolean isEs() {
            return "es".equalsIgnoreCase(provider);
        }
    }
}
