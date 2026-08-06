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
import java.util.UUID;
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

    /** Redis 任务状态 key 前缀:rag:eval:task:{taskId} */
    private static final String TASK_KEY_PREFIX = "rag:eval:task:";
    /** Redis 防重锁 key 前缀:rag:eval:dataset:{datasetId} -> 运行中的 taskId */
    private static final String DATASET_KEY_PREFIX = "rag:eval:dataset:";
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
    public String submit(Long datasetId) {
        // 防重:同一库同时只允许一个评测任务在跑(setnx 占锁,任务结束释放)
        String datasetKey = DATASET_KEY_PREFIX + datasetId;
        String taskId = UUID.randomUUID().toString().replace("-", "");
        if (!redisService.setIfAbsent(datasetKey, taskId, TASK_TTL)) {
            throw new IllegalStateException("该知识库已有评测任务进行中，请等待完成后再试");
        }

        RetrievalEvalTask task = new RetrievalEvalTask();
        task.setTaskId(taskId);
        task.setDatasetId(datasetId);
        task.setStatus(RetrievalEvalTask.STATUS_PENDING);
        task.setTotal(0);
        task.setDone(0);
        task.setCurrentStep("已提交，排队中...");
        task.setCreatedAt(System.currentTimeMillis());
        redisService.set(TASK_KEY_PREFIX + taskId, task, TASK_TTL);

        taskExecutor.execute(() -> runAsync(taskId, datasetId));
        log.info("提交异步评测任务: taskId={}, datasetId={}", taskId, datasetId);
        return taskId;
    }

    /**
     * 后台串行执行评测:逐条 hybrid 检索 + rerank,每条完成更新 Redis 进度,全部完成写结果。
     *
     * <p>内部串行(不并行)以保护内存有限的 embedding/reranker 下游;受 taskExecutor(core=2)
     * 限制,全局同时最多 2 个评测任务,且与文件向量化共享池天然排队。</p>
     */
    private void runAsync(String taskId, Long datasetId) {
        String taskKey = TASK_KEY_PREFIX + taskId;
        try {
            List<EvalQueryEntity> entities = listByDataset(datasetId);
            List<EvalQuery> queries = entities.stream().map(e -> {
                EvalQuery q = new EvalQuery();
                q.setQuery(e.getQuery());
                q.setExpectedSource(e.getExpectedSource());
                q.setExpectedSnippet(e.getExpectedSnippet());
                return q;
            }).toList();

            DatasetEntity dataset = datasetService.getById(datasetId);
            if (dataset == null) {
                throw new IllegalArgumentException("知识库不存在: " + datasetId);
            }

            RetrievalEvalTask task = getTask(taskId);
            task.setStatus(RetrievalEvalTask.STATUS_RUNNING);
            task.setTotal(queries.size());
            task.setCurrentStep(queries.isEmpty() ? "无评测用例" : "评测中 0/" + queries.size());
            redisService.set(taskKey, task, TASK_TTL);

            List<RetrievalEvalResult.Item> items = new ArrayList<>(queries.size());
            int done = 0;
            for (EvalQuery q : queries) {
                items.add(retrievalEvaluator.evaluateSingle(dataset, q));
                done++;
                task.setDone(done);
                task.setCurrentStep("评测中 " + done + "/" + queries.size());
                redisService.set(taskKey, task, TASK_TTL);
            }

            RetrievalEvalResult result = retrievalEvaluator.aggregate(queries.size(), items);
            task.setResult(result);
            task.setStatus(RetrievalEvalTask.STATUS_COMPLETED);
            task.setCurrentStep("完成");
            redisService.set(taskKey, task, TASK_TTL);
            log.info("异步评测完成: taskId={}, datasetId={}, total={}, recall@5={}, mrr={}",
                    taskId, datasetId, result.getTotal(), result.getRecallAtK(), result.getMrr());
        } catch (Exception e) {
            log.error("异步评测失败: taskId={}, datasetId={}", taskId, datasetId, e);
            RetrievalEvalTask task = getTask(taskId);
            if (task != null) {
                task.setStatus(RetrievalEvalTask.STATUS_FAILED);
                task.setErrorMessage(e.getMessage());
                redisService.set(taskKey, task, TASK_TTL);
            }
        } finally {
            // 释放防重锁,允许再次提交
            redisService.delete(DATASET_KEY_PREFIX + datasetId);
        }
    }

    @Override
    public RetrievalEvalTask getTask(String taskId) {
        return redisService.get(TASK_KEY_PREFIX + taskId, RetrievalEvalTask.class);
    }
}
