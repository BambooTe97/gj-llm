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

    /** rerank 分数阈值，低于此值视为无可靠答案（Phase 3 护栏）。 */
    private double rerankScoreThreshold = 0.3;

    /** LLM 上下文总字符预算，防止父块拼接后溢出 gemma2:2b 的 8k token 窗口。 */
    private int contextBudgetChars = 3500;

    /** 可选：LLM Contextual Retrieval（入库时调 LLM 生成上下文），默认关，需评测验证有正收益再开。 */
    private boolean contextualRetrievalEnabled = false;
}
