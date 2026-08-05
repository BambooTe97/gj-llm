package com.gj.llm.rag.eval;

import com.gj.llm.es.service.EsSearchService;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.service.DatasetService;
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
 * <p>对每条评测查询执行 hybridSearch + rerank（复现线上检索链路，但不走查询改写），
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

    private final EsSearchService esSearchService;
    private final RerankerService rerankerService;
    private final DatasetService datasetService;

    public RetrievalEvalResult evaluate(Long datasetId, List<EvalQuery> queries) {
        DatasetEntity dataset = datasetService.getById(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("知识库不存在: " + datasetId);
        }
        String collection = dataset.getCollectionName();

        List<RetrievalEvalResult.Item> items = new ArrayList<>();
        int hits = 0;
        double mrrSum = 0;
        for (EvalQuery q : queries) {
            List<Document> candidates = esSearchService.hybridSearch(collection, q.getQuery(), EVAL_CANDIDATE_K);
            List<Document> ranked = rerankerService.rerank(q.getQuery(), candidates, EVAL_TOP_K);

            RetrievalEvalResult.Item item = new RetrievalEvalResult.Item();
            item.setQuery(q.getQuery());
            item.setTopScore(ranked.isEmpty() ? 0.0 : scoreOf(ranked.get(0)));

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
                hits++;
                mrrSum += 1.0 / rank;
            }
            items.add(item);
        }

        RetrievalEvalResult result = new RetrievalEvalResult();
        result.setTotal(queries.size());
        result.setRecallAtK(queries.isEmpty() ? 0 : (double) hits / queries.size());
        result.setMrr(queries.isEmpty() ? 0 : mrrSum / queries.size());
        result.setItems(items);
        log.info("检索评测完成: datasetId={}, total={}, recall@{}={}, mrr={}",
                datasetId, queries.size(), EVAL_TOP_K, result.getRecallAtK(), result.getMrr());
        return result;
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
            if (src != null && src.toString().contains(q.getExpectedSource())) {
                return true;
            }
        }
        return false;
    }

    private double scoreOf(Document d) {
        return d.getScore() != null ? d.getScore() : 0.0;
    }
}
