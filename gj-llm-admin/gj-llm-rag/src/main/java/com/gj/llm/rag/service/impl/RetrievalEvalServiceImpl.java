package com.gj.llm.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gj.llm.rag.entity.DatasetEntity;
import com.gj.llm.rag.entity.EvalQueryEntity;
import com.gj.llm.rag.eval.EvalQuery;
import com.gj.llm.rag.eval.RetrievalEvalResult;
import com.gj.llm.rag.eval.RetrievalEvalTask;
import com.gj.llm.rag.eval.RetrievalEvaluator;
import com.gj.llm.rag.mapper.EvalQueryMapper;
import com.gj.llm.rag.service.DatasetService;
import com.gj.llm.rag.service.RetrievalEvalService;
import com.gj.llm.redis.service.RedisService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 检索评测服务实现 -- 评测用例持久化(CRUD + 导入) + 异步跑评测。
 *
 * <p>跑评测改为异步任务化:{@link #submit} 立即返回 taskId,后台线程池串行执行
 * (逐条 hybrid 检索 + rerank,不抬高下游 embedding/reranker 峰值),进度写 Redis
 * 供前端轮询,完成写入完整结果。任务状态带 TTL(2h)自动清理,不污染 DB。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalEvalServiceImpl extends ServiceImpl<EvalQueryMapper, EvalQueryEntity> implements RetrievalEvalService {

    /** Redis 任务状态 key 前缀:rag:eval:task:{datasetId} -- 一个库一个最近任务槽位 */
    private static final String TASK_KEY_PREFIX = "rag:eval:task:";
    /** Redis 防重锁 key 前缀:rag:eval:lock:{datasetId} -- 运行中占位,完成释放(与 task key 分离,task 保留 2h 供恢复) */
    private static final String LOCK_KEY_PREFIX = "rag:eval:lock:";
    private static final Duration TASK_TTL = Duration.ofHours(2);

    private final RetrievalEvaluator retrievalEvaluator;
    private final RedisService redisService;
    private final DatasetService datasetService;

    /** 通用异步线程池(复用 AsyncThreadPoolConfig.taskExecutor,core=2 天然限流,保护下游) */
    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

    @Override
    public List<EvalQueryEntity> listByDataset(Long datasetId) {
        return list(new LambdaQueryWrapper<EvalQueryEntity>()
                .eq(EvalQueryEntity::getDatasetId, datasetId)
                .orderByDesc(EvalQueryEntity::getCreatedAt));
    }

    @Override
    @Transactional
    public EvalQueryEntity create(Long datasetId, EvalQuery request) {
        EvalQueryEntity entity = EvalQueryEntity.builder()
                .datasetId(datasetId)
                .query(request.getQuery())
                .expectedSource(request.getExpectedSource())
                .expectedSnippet(request.getExpectedSnippet())
                .build();
        save(entity);
        log.info("新增评测用例: datasetId={}, id={}", datasetId, entity.getId());
        return entity;
    }

    @Override
    @Transactional
    public EvalQueryEntity update(Long id, EvalQuery request) {
        EvalQueryEntity existing = getById(id);
        if (existing == null) {
            throw new RuntimeException("评测用例不存在: id=" + id);
        }
        if (request.getQuery() != null) existing.setQuery(request.getQuery());
        if (request.getExpectedSource() != null) existing.setExpectedSource(request.getExpectedSource());
        if (request.getExpectedSnippet() != null) existing.setExpectedSnippet(request.getExpectedSnippet());
        updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public int importQueries(Long datasetId, List<EvalQuery> queries) {
        List<EvalQueryEntity> entities = queries.stream().map(q -> EvalQueryEntity.builder()
                .datasetId(datasetId)
                .query(q.getQuery())
                .expectedSource(q.getExpectedSource())
                .expectedSnippet(q.getExpectedSnippet())
                .build()).toList();
        saveBatch(entities);
        log.info("导入评测用例: datasetId={}, 数量={}", datasetId, entities.size());
        return entities.size();
    }

    @Override
    public RetrievalEvalResult run(Long datasetId) {
        List<EvalQueryEntity> entities = listByDataset(datasetId);
        List<EvalQuery> queries = entities.stream().map(e -> {
            EvalQuery q = new EvalQuery();
            q.setQuery(e.getQuery());
            q.setExpectedSource(e.getExpectedSource());
            q.setExpectedSnippet(e.getExpectedSnippet());
            return q;
        }).toList();
        return retrievalEvaluator.evaluate(datasetId, queries);
    }

    // ==================== 异步任务化评测 ====================

    @Override
    public void submit(Long datasetId, List<Long> queryIds) {
        // 防重:同一库同时只允许一个评测任务在跑(setnx 占锁,任务结束释放)
        if (!redisService.setIfAbsent(LOCK_KEY_PREFIX + datasetId, "1", TASK_TTL)) {
            throw new IllegalStateException("该知识库已有评测任务进行中，请等待完成后再试");
        }
        String taskKey = TASK_KEY_PREFIX + datasetId;
        RetrievalEvalTask task = new RetrievalEvalTask();
        task.setTaskId(String.valueOf(datasetId)); // 仅作日志标识,Redis key 用 datasetId
        task.setDatasetId(datasetId);
        task.setStatus(RetrievalEvalTask.STATUS_PENDING);
        task.setTotal(0);
        task.setDone(0);
        task.setCurrentStep("已提交，排队中...");
        task.setCreatedAt(System.currentTimeMillis());
        redisService.set(taskKey, task, TASK_TTL);

        taskExecutor.execute(() -> runAsync(datasetId, queryIds));
        log.info("提交异步评测任务: datasetId={}, 范围={}", datasetId,
                queryIds == null || queryIds.isEmpty() ? "全量" : queryIds.size() + "条");
    }

    /**
     * 后台串行执行评测:逐条 hybrid 检索 + rerank,每条完成更新 Redis 进度,全部完成写结果。
     *
     * <p>内部串行(不并行)以保护内存有限的 embedding/reranker 下游;受 taskExecutor(core=2)
     * 限制,全局同时最多 2 个评测任务,且与文件向量化共享池天然排队。</p>
     */
    private void runAsync(Long datasetId, List<Long> queryIds) {
        String taskKey = TASK_KEY_PREFIX + datasetId;
        try {
            // 选择性测评:queryIds 空=全量,非空=只跑指定用例(按 datasetId 过滤防跨库)
            List<EvalQueryEntity> entities = (queryIds == null || queryIds.isEmpty())
                    ? listByDataset(datasetId)
                    : listByIds(queryIds).stream()
                            .filter(e -> datasetId.equals(e.getDatasetId()))
                            .toList();
            DatasetEntity dataset = datasetService.getById(datasetId);
            if (dataset == null) {
                throw new IllegalArgumentException("知识库不存在: " + datasetId);
            }

            RetrievalEvalTask task = getTaskByDataset(datasetId);
            task.setStatus(RetrievalEvalTask.STATUS_RUNNING);
            task.setTotal(entities.size());
            task.setCurrentStep(entities.isEmpty() ? "无评测用例" : "评测中 0/" + entities.size());
            redisService.set(taskKey, task, TASK_TTL);

            List<RetrievalEvalResult.Item> items = new ArrayList<>(entities.size());
            int done = 0;
            for (EvalQueryEntity e : entities) {
                EvalQuery q = new EvalQuery();
                q.setQuery(e.getQuery());
                q.setExpectedSource(e.getExpectedSource());
                q.setExpectedSnippet(e.getExpectedSnippet());
                RetrievalEvalResult.Item item = retrievalEvaluator.evaluateSingle(dataset, q);
                item.setQueryId(e.getId());
                items.add(item);
                done++;
                task.setDone(done);
                task.setCurrentStep("评测中 " + done + "/" + entities.size());
                redisService.set(taskKey, task, TASK_TTL);
            }

            RetrievalEvalResult result = retrievalEvaluator.aggregate(entities.size(), items, dataset.getRerankScoreThreshold());
            task.setResult(result);
            task.setStatus(RetrievalEvalTask.STATUS_COMPLETED);
            task.setCurrentStep("完成");
            redisService.set(taskKey, task, TASK_TTL);
            log.info("异步评测完成: datasetId={}, total={}, recall@5={}, mrr={}",
                    datasetId, result.getTotal(), result.getRecallAtK(), result.getMrr());
        } catch (Exception e) {
            log.error("异步评测失败: datasetId={}", datasetId, e);
            RetrievalEvalTask task = getTaskByDataset(datasetId);
            if (task != null) {
                task.setStatus(RetrievalEvalTask.STATUS_FAILED);
                task.setErrorMessage(e.getMessage());
                redisService.set(taskKey, task, TASK_TTL);
            }
        } finally {
            // 释放防重锁,允许再次提交(task 保留 2h 供前端恢复)
            redisService.delete(LOCK_KEY_PREFIX + datasetId);
        }
    }

    @Override
    public RetrievalEvalTask getTaskByDataset(Long datasetId) {
        return redisService.get(TASK_KEY_PREFIX + datasetId, RetrievalEvalTask.class);
    }
}
