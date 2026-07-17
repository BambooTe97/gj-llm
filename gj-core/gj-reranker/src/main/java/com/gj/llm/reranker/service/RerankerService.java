package com.gj.llm.reranker.service;

import com.gj.llm.common.util.JacksonUtils;
import com.gj.llm.reranker.config.RerankerProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Re-Ranker 精排服务 —— 调用 TEI / 兼容 API 对粗排候选做 Cross-Encoder 精细打分。
 *
 * <p>API 约定（TEI /rerank 格式）：</p>
 * <pre>
 * POST /rerank
 * { "query": "...", "texts": ["doc1", "doc2"], "truncate": true }
 *
 * Response: [ {"index": 0, "score": 0.95}, {"index": 1, "score": 0.32} ]
 * </pre>
 */
@Slf4j
@Service
public class RerankerService {

    private final RestClient restClient;
    private final RerankerProperties properties;
    private volatile boolean available = false;

    public RerankerService(RerankerProperties properties) {
        this.properties = properties;
        this.restClient = buildRestClient(properties.getHost(), properties.getPort(), properties.getTimeout());
    }

    @PostConstruct
    void checkConnectivity() {
        if (!properties.isEnabled()) {
            log.info("Re-Ranker 已禁用 (gj.llm.reranker.enabled=false)，将使用粗排结果");
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", "ping");
            body.put("texts", List.of("pong"));
            body.put("truncate", true);

            String resp = restClient.post()
                    .uri("/rerank")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            List<Map<String, Object>> items = JacksonUtils.fromJson(resp, List.class);
            available = items != null && !items.isEmpty();
            log.info("✅ Re-Ranker 连通性检查成功: host={}:{}, 返回 {} 条",
                    properties.getHost(), properties.getPort(), items != null ? items.size() : 0);
        } catch (Exception e) {
            available = false;
            log.warn("❌ Re-Ranker 连通性检查失败: host={}:{}, error={}。将降级为粗排。",
                    properties.getHost(), properties.getPort(), e.getMessage());
        }
    }

    private static RestClient buildRestClient(String host, int port, int timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl("http://" + host + ":" + port)
                .requestFactory(factory)
                .build();
    }

    /**
     * 对候选文档列表重排序，返回 topK 个结果。
     *
     * @param query      查询文本
     * @param documents  粗排候选文档（建议 ≤20 条，避免 reranker 耗时过长）
     * @param topK       最终返回数量
     * @return 按 rerank 分数降序排列的文档，分数已替换为 rerank 分数
     */
    public List<Document> rerank(String query, List<Document> documents, int topK) {
        if (!properties.isEnabled()) {
            log.debug("Re-Ranker 已禁用，透传粗排结果");
            return documents.stream().limit(topK).toList();
        }
        if (documents.isEmpty()) {
            return documents;
        }

        List<String> texts = documents.stream().map(Document::getText).toList();

        double[] coarseScores = documents.stream().mapToDouble(d -> d.getScore() != null ? d.getScore() : 0).toArray();

        try {
            long start = System.currentTimeMillis();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            body.put("texts", texts);
            body.put("truncate", true);

            String respJson = restClient.post()
                    .uri("/rerank")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            List<RerankItem> items = JacksonUtils.listFromJson(respJson, RerankItem.class);
            Map<Integer, Double> indexScore = new LinkedHashMap<>();
            for (RerankItem item : items) {
                indexScore.put(item.index(), item.score());
            }

            List<Document> reranked = indexScore.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .limit(topK)
                    .map(e -> {
                        Document doc = documents.get(e.getKey());
                        return Document.builder()
                                .text(doc.getText())
                                .score(e.getValue())
                                .metadata(doc.getMetadata())
                                .build();
                    })
                    .toList();

            long cost = System.currentTimeMillis() - start;
            double[] fineScores = reranked.stream().mapToDouble(Document::getScore).toArray();
            log.info("Re-Ranker 精排完成: candidates={}, topK={}, 粗排分=[{}], 精排分=[{}], 耗时={}ms",
                    texts.size(), reranked.size(),
                    formatScores(coarseScores, 5),
                    formatScores(fineScores, 5),
                    cost);

            return reranked;
        } catch (Exception e) {
            log.warn("❌ Re-Ranker 调用失败，降级为原始粗排结果: {}", e.getMessage());
            return documents.stream().limit(topK).toList();
        }
    }

    private static String formatScores(double[] scores, int max) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(scores.length, max);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.4f", scores[i]));
        }
        if (scores.length > max) sb.append(", ...");
        return sb.toString();
    }

    private record RerankItem(int index, double score) {}
}
