package com.gj.llm.rag.service.impl;

import com.gj.llm.rag.config.RagProperties;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.model.RankedTestItem;
import com.gj.llm.rag.model.TestRankedResult;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.rag.service.HybridSearcher;
import com.gj.llm.rag.service.QueryRewriter;
import com.gj.llm.rag.service.Reference;
import com.gj.llm.rag.service.RetrievalResult;
import com.gj.llm.rag.service.RetrievalService;
import com.gj.llm.reranker.service.RerankerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索服务实现 -- RAG 检索编排(改写 -> 多路粗排 -> 精排 -> 父子召回 -> 质量护栏)。
 *
 * <p>检索是 rag 的本分,此处把完整管道收敛在模块内,对外只暴露 {@link RetrievalResult}。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private final HybridSearcher hybridSearcher;
    private final RerankerService rerankerService;
    private final QueryRewriter queryRewriter;
    private final DatasetService datasetService;
    private final RagProperties ragProperties;

    /** RAG 检索最终返回数量 */
    private static final int TOP_K = 5;
    /** 每个查询变体的粗排返回数 */
    private static final int VARIANT_TOP_K = 8;
    /** 合并后送 re-ranker 的最大候选数 */
    private static final int MAX_RERANK_CANDIDATES = 30;

    @Override
    public RetrievalResult retrieve(String query, Long datasetId) {
        if (datasetId == null) {
            return RetrievalResult.empty();
        }
        try {
            long t0 = System.currentTimeMillis();
            DatasetEntity dataset = datasetService.getById(datasetId);
            if (dataset == null) {
                return RetrievalResult.empty();
            }

            // ① 查询改写:生成多个检索变体(含原始查询),扩大粗排覆盖面
            List<String> queries = queryRewriter.rewrite(query);

            // ② 多路粗排:每个变体分别检索,合并去重(同文本保留最高分)
            Map<String, Document> merged = new LinkedHashMap<>();
            for (String q : queries) {
                List<Document> hits = hybridSearcher.search(dataset.getCollectionName(), q, VARIANT_TOP_K);
                for (Document doc : hits) {
                    String key = doc.getText().trim();
                    Document existing = merged.get(key);
                    if (existing == null || doc.getScore() > existing.getScore()) {
                        merged.put(key, doc);
                    }
                }
            }
            List<Document> candidates = new ArrayList<>(merged.values());
            candidates.sort(Comparator.comparingDouble(Document::getScore).reversed());
            if (candidates.size() > MAX_RERANK_CANDIDATES) {
                candidates = candidates.subList(0, MAX_RERANK_CANDIDATES);
            }
            log.info("[Retrieval] 多路检索完成, 合并去重后候选 {} 条", candidates.size());

            // ③ 精排:Cross-Encoder Re-Ranker 重打分
            List<Document> docs = rerankerService.rerank(query, candidates, TOP_K);

            // ④ 父子召回:按 parent_id 去重(同父块保留最高分)
            List<Document> deduped = dedupByParent(docs);

            // ⑤ 质量护栏:过滤低于 rerank 阈值的弱结果
            double threshold = ragProperties.getRerankScoreThreshold();
            List<Document> confident = deduped.stream()
                    .filter(d -> scoreOf(d) >= threshold)
                    .toList();

            if (confident.isEmpty()) {
                log.info("[Retrieval] 无可靠检索结果(rerank 阈值 {}),走我不知道分支, 耗时: {}ms",
                        threshold, System.currentTimeMillis() - t0);
                return new RetrievalResult("", List.of(), true);
            }

            String context = buildParentContext(confident);
            List<Reference> references = buildReferences(confident);
            log.info("[Retrieval] 命中 {} 父块, 可信 {} 条, context.len={}, 耗时: {}ms",
                    deduped.size(), confident.size(), context.length(), System.currentTimeMillis() - t0);
            return new RetrievalResult(context, references, false);
        } catch (Exception e) {
            log.warn("[Retrieval] 检索失败,返回空结果: {}", e.getMessage());
            return RetrievalResult.empty();
        }
    }

    @Override
    public TestRankedResult retrieveRanked(String query, Long datasetId, int topK) {
        // 线上精排采纳阈值:精排分 ≥ 此值的结果会在对话中被采用,随结果返回供页面标线
        double threshold = ragProperties.getRerankScoreThreshold();
        if (datasetId == null) {
            return TestRankedResult.empty(false, threshold);
        }
        try {
            DatasetEntity dataset = datasetService.getById(datasetId);
            if (dataset == null) {
                return TestRankedResult.empty(false, threshold);
            }

            // 粗排:取一个比 topK 大的候选池供精排重排
            int pool = Math.min(Math.max(topK * 2, 10), 20);
            List<Document> candidates = hybridSearcher.search(dataset.getCollectionName(), query, pool);
            if (candidates.isEmpty()) {
                return TestRankedResult.empty(rerankerService.isAvailable(), threshold);
            }

            // 粗排分按文本建索引,供精排后回填对照
            Map<String, Double> coarseByText = new LinkedHashMap<>();
            for (Document c : candidates) {
                coarseByText.put(c.getText(), c.getScore() != null ? c.getScore() : 0.0);
            }

            // 精排
            List<Document> ranked = rerankerService.rerank(query, candidates, topK);

            List<RankedTestItem> items = new ArrayList<>();
            for (int i = 0; i < ranked.size(); i++) {
                Document d = ranked.get(i);
                double rerankScore = d.getScore() != null ? d.getScore() : 0.0;
                double coarseScore = coarseByText.getOrDefault(d.getText(), 0.0);
                String source = (String) d.getMetadata().get("source");
                Object datasetFileId = d.getMetadata().get("dataset_file_id");
                items.add(new RankedTestItem(i + 1, d.getText(), coarseScore, rerankScore, source, datasetFileId));
            }
            log.info("[Retrieval] 精排测试完成: candidates={}, 返回 {} 条, rerankerAvailable={}, threshold={}", candidates.size(), items.size(), rerankerService.isAvailable(), threshold);
            return new TestRankedResult(rerankerService.isAvailable(), threshold, items);
        } catch (Exception e) {
            log.warn("[Retrieval] 精排测试失败: {}", e.getMessage());
            return TestRankedResult.empty(false, threshold);
        }
    }

    // ==================== 辅助方法 ====================

    private List<Reference> buildReferences(List<Document> docs) {
        List<Reference> refs = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            double score = doc.getScore() != null ? doc.getScore() : 0.0;
            String text = doc.getText();
            String content = text != null ? text.substring(0, Math.min(text.length(), 200)) : "";
            String source = (String) doc.getMetadata().get("source");
            Object datasetFileId = doc.getMetadata().get("dataset_file_id");
            refs.add(new Reference(i + 1, content, Math.round(score * 1000.0) / 1000.0, source, datasetFileId));
        }
        return refs;
    }

    /**
     * 父子召回去重:按 parent_id 合并同父块的子块(保留最高分)。
     * parent_id 为 null(旧数据)时视为独立块,不合并,保持向后兼容。
     */
    private List<Document> dedupByParent(List<Document> docs) {
        Map<String, Document> byParent = new LinkedHashMap<>();
        for (Document d : docs) {
            String pid = (String) d.getMetadata().get("parent_id");
            String key = pid != null ? pid : "child:" + d.getId();
            Document ex = byParent.get(key);
            if (ex == null || scoreOf(d) > scoreOf(ex)) {
                byParent.put(key, d);
            }
        }
        List<Document> result = new ArrayList<>(byParent.values());
        result.sort(Comparator.comparingDouble(this::scoreOf).reversed());
        return result;
    }

    private double scoreOf(Document d) {
        return d.getScore() != null ? d.getScore() : 0.0;
    }

    /**
     * 用父块完整上下文构建 LLM context(parent_content 为 null 时回退子块文本)。
     * 受 {@link RagProperties#getContextBudgetChars()} 字符预算约束,防止溢出上下文窗口。
     */
    private String buildParentContext(List<Document> deduped) {
        int budget = ragProperties.getContextBudgetChars();
        StringBuilder ctx = new StringBuilder();
        for (Document d : deduped) {
            String parentContent = (String) d.getMetadata().get("parent_content");
            String seg = (parentContent != null && !parentContent.isBlank()) ? parentContent : d.getText();
            if (ctx.length() > 0 && ctx.length() + seg.length() + 2 > budget) {
                break; // 超预算截断,保留已累加的高分父块
            }
            if (ctx.length() > 0) {
                ctx.append("\n\n");
            }
            ctx.append(seg);
        }
        return ctx.toString();
    }
}
