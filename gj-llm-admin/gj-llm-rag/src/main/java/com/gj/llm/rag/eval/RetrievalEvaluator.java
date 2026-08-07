package com.gj.llm.rag.eval;

import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.rag.service.HybridSearcher;
import com.gj.llm.reranker.service.RerankerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 离线检索评测器 -- 量化衡量切分/检索改动效果，让调参不盲。
 *
 * <p>对每条评测查询执行 HybridSearcher 混合检索 + rerank（复现线上检索链路，但不走查询改写），
 * 判定期望是否进 top-K，计算 Recall@K 与 MRR。</p>
 *
 * @author zf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalEvaluator {

    private static final int EVAL_TOP_K = 5;
    private static final int EVAL_CANDIDATE_K = 8;

    private final HybridSearcher hybridSearcher;
    private final RerankerService rerankerService;
    private final DatasetService datasetService;

    public RetrievalEvalResult evaluate(Long datasetId, List<EvalQuery> queries) {
        DatasetEntity dataset = datasetService.getById(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("知识库不存在: " + datasetId);
        }
        List<RetrievalEvalResult.Item> items = new ArrayList<>(queries.size());
        for (EvalQuery q : queries) {
            items.add(evaluateSingle(dataset, q));
        }
        RetrievalEvalResult result = aggregate(queries.size(), items, dataset.getRerankScoreThreshold());
        log.info("检索评测完成: datasetId={}, total={}, recall@{}={}, mrr={}",
                datasetId, queries.size(), EVAL_TOP_K, result.getRecallAtK(), result.getMrr());
        return result;
    }

    /**
     * 评测单条查询 -- 执行 hybrid 检索 + rerank,判定期望是否命中,返回该条明细。
     *
     * <p>供异步任务化场景逐条执行 + 进度上报;无跨条状态,可安全串行调用。</p>
     *
     * @param dataset 知识库(取 collectionName)
     * @param q       评测用例
     * @return 该查询的命中明细(topScore/rank/found/expectedScore)
     */
    public RetrievalEvalResult.Item evaluateSingle(DatasetEntity dataset, EvalQuery q) {
        String collection = dataset.getCollectionName();
        List<Document> candidates = hybridSearcher.search(collection, q.getQuery(), EVAL_CANDIDATE_K);
        List<Document> ranked = rerankerService.rerank(q.getQuery(), candidates, EVAL_TOP_K);

        RetrievalEvalResult.Item item = new RetrievalEvalResult.Item();
        item.setQuery(q.getQuery());
        item.setTopScore(ranked.isEmpty() ? 0.0 : scoreOf(ranked.getFirst()));

        int rank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            if (matches(ranked.get(i), q)) {
                rank = i + 1;
                break;
            }
        }
        item.setRank(rank);
        item.setFound(rank > 0);
        if (rank > 0) {
            // 期望文档的精排分，供前端阈值扫描
            item.setExpectedScore(scoreOf(ranked.get(rank - 1)));
        }

        // top-K 候选明细（供前端行展开钻取）：期望命中位标 expected
        List<RetrievalEvalResult.CandidateDetail> candidateDetails = new ArrayList<>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            Document d = ranked.get(i);
            RetrievalEvalResult.CandidateDetail cd = new RetrievalEvalResult.CandidateDetail();
            cd.setText(d.getText());
            cd.setScore(scoreOf(d));
            cd.setSource((String) d.getMetadata().get("source"));
            cd.setExpected(rank > 0 && i == rank - 1);
            candidateDetails.add(cd);
        }
        item.setCandidates(candidateDetails);
        return item;
    }

    /**
     * 聚合逐条明细为评测结果 -- 计算 Recall@K / MRR + 注入当前 rerank 阈值 + 计算推荐阈值。
     *
     * <p>推荐阈值 = 有效召回率(effective recall)仍 ≥ 95%·Recall@5 的最高阈值,
     * 即阈值扫描"拐点前最后一个不掉命中的点"--既能过滤精排低分弱结果,又几乎不损失命中。</p>
     *
     * @param total            用例总数
     * @param items            逐条明细
     * @param currentThreshold 当前知识库配置的 rerank 阈值
     * @return 评测结果
     */
    public RetrievalEvalResult aggregate(int total, List<RetrievalEvalResult.Item> items, double currentThreshold) {
        int hits = 0;
        double mrrSum = 0;
        for (RetrievalEvalResult.Item item : items) {
            if (item.isFound()) {
                hits++;
                mrrSum += 1.0 / item.getRank();
            }
        }
        double recallAtK = total == 0 ? 0 : (double) hits / total;
        RetrievalEvalResult result = new RetrievalEvalResult();
        result.setTotal(total);
        result.setRecallAtK(recallAtK);
        result.setMrr(total == 0 ? 0 : mrrSum / total);
        result.setItems(items);
        result.setRerankScoreThreshold(currentThreshold);

        // 推荐阈值:有效召回率仍 ≥ 95%·Recall@5 的最高阈值
        double recommended = computeRecommendedThreshold(items, total, recallAtK, currentThreshold);
        result.setRecommendedThreshold(recommended);
        double effAtRecommended = total == 0 ? 0
                : items.stream().filter(it -> it.isFound() && it.getExpectedScore() >= recommended).count() / (double) total;
        result.setRecommendationReason(String.format(
                "推荐阈值 %d%%:此阈值下有效召回率 %d%%(Recall@5 的 %d%%), 可在过滤精排低分弱结果的同时几乎不损失命中,再高即开始塌陷.",
                Math.round(recommended * 100), Math.round(effAtRecommended * 100),
                Math.round(recallAtK * 100),
                Math.round(recallAtK > 0 ? effAtRecommended / recallAtK * 100 : 0)));
        return result;
    }

    /**
     * 计算推荐阈值:步长 0.05 扫描 [0.10, 0.95],取有效召回率 ≥ 95%·Recall@5 的最高阈值。
     * effective recall 随阈值单调递减,首个不满足即停;全不满足(Recall@5 过低)时回退当前阈值。
     */
    private double computeRecommendedThreshold(List<RetrievalEvalResult.Item> items, int total,
                                               double recallAtK, double fallback) {
        if (total == 0 || recallAtK <= 0) {
            return fallback;
        }
        double target = recallAtK * 0.95;
        double recommended = fallback;
        for (double t = 0.10; t <= 0.95 + 1e-9; t += 0.05) {
            final double tt = t;
            double eff = items.stream()
                    .filter(it -> it.isFound() && it.getExpectedScore() >= tt)
                    .count() / (double) total;
            if (eff >= target) {
                recommended = t;
            } else {
                break;
            }
        }
        return recommended;
    }

    private boolean matches(Document d, EvalQuery q) {
        if (q.getExpectedSnippet() != null && !q.getExpectedSnippet().isBlank()) {
            String snip = q.getExpectedSnippet();
            if (d.getText() != null && d.getText().contains(snip)) {
                return true;
            }
            String parentContent = (String) d.getMetadata().get("parent_content");
            if (parentContent != null && parentContent.contains(snip)) {
                return true;
            }
        }
        if (q.getExpectedSource() != null && !q.getExpectedSource().isBlank()) {
            Object src = d.getMetadata().get("source");
            return src != null && src.toString().contains(q.getExpectedSource());
        }
        return false;
    }

    private double scoreOf(Document d) {
        return d.getScore() != null ? d.getScore() : 0.0;
    }
}
