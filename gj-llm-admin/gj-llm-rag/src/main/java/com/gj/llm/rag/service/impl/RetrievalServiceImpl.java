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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 检索服务实现 -- RAG 检索编排(多库路由结果 -> 改写 -> 多库并发粗排 -> 精排 -> 父子召回 -> 逐库质量护栏)。
 *
 * <p>检索是 rag 的本分,此处把完整管道收敛在模块内,对外只暴露 {@link RetrievalResult}。
 * 单库入口是规模为 1 的多库特例,委托同一条管线,保证行为一致。</p>
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

    /**
     * 多库并发检索线程池 -- 守护线程(不阻碍 JVM 退出),固定 8 并发
     * (扇出上限即库数 ≤8,保护下游 ES/Milvus;不复用 taskExecutor 是避免与评测任务争抢)。
     */
    private static final ExecutorService SEARCH_POOL = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "rag-fanout-search");
        t.setDaemon(true);
        return t;
    });

    /** 粗排候选:文档 + 来源库(跨库合并、逐库阈值过滤、引用出处标注都需要知道来源) */
    private record Candidate(Document doc, DatasetEntity dataset) {

        String text() {
            return doc.getText().trim();
        }

        double score() {
            return doc.getScore() != null ? doc.getScore() : 0.0;
        }
    }

    @Override
    public RetrievalResult retrieve(String query, Long datasetId) {
        // 单库是规模为 1 的多库:委托统一管线,避免两套实现漂移
        return retrieve(query, datasetId == null ? List.of() : List.of(datasetId));
    }

    @Override
    public RetrievalResult retrieve(String query, List<Long> datasetIds) {
        if (datasetIds == null || datasetIds.isEmpty()) {
            return RetrievalResult.empty();
        }
        try {
            long t0 = System.currentTimeMillis();
            // 载入目标库(去重保序;不存在的库跳过,不阻断其余库)
            List<DatasetEntity> datasets = datasetIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(datasetService::getById)
                    .filter(Objects::nonNull)
                    .toList();
            if (datasets.isEmpty()) {
                return RetrievalResult.empty();
            }

            // ① 查询改写:生成多个检索变体(含原始查询),扩大粗排覆盖面(改写与路由职责分离,不在此合并)
            List<String> queries = queryRewriter.rewrite(query);

            // ② 多库并发粗排:每库跑完整变体循环,库内按文本去重(同文本保留最高分);
            //    并发降低多库墙钟延迟,单库请求等价原实现
            List<CompletableFuture<List<Candidate>>> futures = datasets.stream()
                    .map(ds -> CompletableFuture.supplyAsync(() -> searchDataset(ds, queries), SEARCH_POOL)
                            .exceptionally(e -> {
                                log.warn("[Retrieval] 库「{}」检索异常(跳过该库): {}", ds.getName(), e.getMessage());
                                return List.of();
                            }))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 跨库合并去重(同文本保留最高分;全局统一 embedding 模型,粗排分跨库可比)
            Map<String, Candidate> merged = new LinkedHashMap<>();
            for (CompletableFuture<List<Candidate>> f : futures) {
                for (Candidate c : f.join()) {
                    Candidate existing = merged.get(c.text());
                    if (existing == null || c.score() > existing.score()) {
                        merged.put(c.text(), c);
                    }
                }
            }
            List<Candidate> candidates = new ArrayList<>(merged.values());
            candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
            if (candidates.size() > MAX_RERANK_CANDIDATES) {
                candidates = candidates.subList(0, MAX_RERANK_CANDIDATES);
            }
            log.info("[Retrieval] 多库检索完成, 目标 {} 库, 合并去重后候选 {} 条, 耗时: {}ms",
                    datasets.size(), candidates.size(), System.currentTimeMillis() - t0);

            // ③ 精排:Cross-Encoder Re-Ranker 重打分(文本对打分与 embedding 空间无关,跨库全局可比)
            List<Document> docs = rerankerService.rerank(query,
                    candidates.stream().map(Candidate::doc).toList(), TOP_K);

            // 精排重建了 Document(保留原 metadata):按文本回关联来源库,score 已是精排分
            List<Candidate> reranked = docs.stream()
                    .map(d -> {
                        Candidate origin = merged.get(d.getText().trim());
                        return origin == null ? null : new Candidate(d, origin.dataset());
                    })
                    .filter(Objects::nonNull)
                    .toList();

            // ④ 父子召回:按 parent_id 去重(同父块保留最高分)
            List<Candidate> deduped = dedupByParent(reranked);

            // ⑤ 质量护栏:按各自来源库的 rerank 阈值过滤弱结果(阈值随库配置)
            List<Candidate> confident = deduped.stream()
                    .filter(c -> c.score() >= c.dataset().getRerankScoreThreshold())
                    .toList();

            if (confident.isEmpty()) {
                log.info("[Retrieval] 无可靠检索结果(按各自库 rerank 阈值过滤),走我不知道分支, 耗时: {}ms",
                        System.currentTimeMillis() - t0);
                return new RetrievalResult("", List.of(), true);
            }

            // ⑥ 构建编号上下文与引用列表(同一次遍历,编号 1:1 对应;每条引用自带各自库信息)
            CitedContext cited = buildCitedContext(confident);
            log.info("[Retrieval] 命中 {} 父块, 可信 {} 条, 引用 {} 条, context.len={}, 耗时: {}ms",
                    deduped.size(), confident.size(), cited.references().size(),
                    cited.context().length(), System.currentTimeMillis() - t0);
            return new RetrievalResult(cited.context(), cited.references(), false);
        } catch (Exception e) {
            log.warn("[Retrieval] 检索失败,返回空结果: {}", e.getMessage());
            return RetrievalResult.empty();
        }
    }

    /** 单库粗排:跑完所有查询变体,库内按文本去重(同文本保留最高分) */
    private List<Candidate> searchDataset(DatasetEntity dataset, List<String> queries) {
        Map<String, Document> byText = new LinkedHashMap<>();
        for (String q : queries) {
            List<Document> hits = hybridSearcher.search(dataset.getCollectionName(), q, VARIANT_TOP_K);
            for (Document doc : hits) {
                String key = doc.getText().trim();
                Document existing = byText.get(key);
                if (existing == null || scoreOf(doc) > scoreOf(existing)) {
                    byText.put(key, doc);
                }
            }
        }
        List<Candidate> result = new ArrayList<>(byText.size());
        for (Document d : byText.values()) {
            result.add(new Candidate(d, dataset));
        }
        return result;
    }

    @Override
    public TestRankedResult retrieveRanked(String query, Long datasetId, int topK) {
        // 线上精排采纳阈值:精排分 ≥ 此值的结果会在对话中被采用,随结果返回供页面标线(阈值随知识库配置)
        if (datasetId == null) {
            return TestRankedResult.empty(false, 0.3);
        }
        double threshold = 0.3; // 兜底,dataset 取到后覆盖
        try {
            DatasetEntity dataset = datasetService.getById(datasetId);
            if (dataset == null) {
                return TestRankedResult.empty(false, threshold);
            }
            threshold = dataset.getRerankScoreThreshold();

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

    /**
     * 编号上下文 + 引用列表 -- 单次遍历同时产出,保证【片段n】编号与 {@code Reference.rank}
     * 严格 1:1 对应(含字符预算截断联动:被预算裁掉的片段不出现在引用列表,
     * 模型看不到的内容前端也不标注,杜绝"角标指向模型未见过的内容")。
     */
    private CitedContext buildCitedContext(List<Candidate> candidates) {
        int budget = ragProperties.getContextBudgetChars();
        StringBuilder ctx = new StringBuilder();
        List<Reference> refs = new ArrayList<>();
        int rank = 0;
        for (Candidate c : candidates) {
            DatasetEntity dataset = c.dataset();
            Document d = c.doc();
            // 片段正文:优先父块完整上下文(parent_content 为 null 时回退子块文本)
            String parentContent = (String) d.getMetadata().get("parent_content");
            String segBody = (parentContent != null && !parentContent.isBlank()) ? parentContent : d.getText();
            String source = (String) d.getMetadata().get("source");
            rank++;

            // 片段头:编号 + 来源标注(知识库/文件),供 LLM 标注引用编号时锚定
            String segHead = "【片段" + rank + "】(来源: 知识库「" + dataset.getName() + "」 文档「"
                    + (source != null ? source : "未知") + "」)\n";
            if (ctx.length() > 0 && ctx.length() + segHead.length() + segBody.length() + 2 > budget) {
                break; // 超预算截断,保留已累加的高分父块
            }
            if (ctx.length() > 0) {
                ctx.append("\n\n");
            }
            ctx.append(segHead).append(segBody);

            // 引用内容与模型所见同源(父块),截断 200 字供前端展示
            refs.add(new Reference(rank, truncate(segBody, 200), round3(c.score()),
                    source, dataset.getName(), dataset.getId(), d.getMetadata().get("dataset_file_id")));
        }
        return new CitedContext(ctx.toString(), refs);
    }

    /** 编号上下文 + 引用列表的打包结构(见 {@link #buildCitedContext}) */
    private record CitedContext(String context, List<Reference> references) {
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.substring(0, Math.min(s.length(), max));
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /**
     * 父子召回去重:按 parent_id 合并同父块的子块(保留最高分)。
     * parent_id 为 null(旧数据)时视为独立块,不合并,保持向后兼容。
     */
    private List<Candidate> dedupByParent(List<Candidate> candidates) {
        Map<String, Candidate> byParent = new LinkedHashMap<>();
        for (Candidate c : candidates) {
            String pid = (String) c.doc().getMetadata().get("parent_id");
            String key = pid != null ? pid : "child:" + c.doc().getId();
            Candidate ex = byParent.get(key);
            if (ex == null || c.score() > ex.score()) {
                byParent.put(key, c);
            }
        }
        List<Candidate> result = new ArrayList<>(byParent.values());
        result.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return result;
    }

    private double scoreOf(Document d) {
        return d.getScore() != null ? d.getScore() : 0.0;
    }
}
