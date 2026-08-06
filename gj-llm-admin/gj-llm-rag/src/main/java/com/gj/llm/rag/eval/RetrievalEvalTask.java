package com.gj.llm.rag.eval;

import lombok.Data;

/**
 * 检索评测异步任务状态 -- 供前端轮询进度与结果。
 *
 * <p>跑评测改为异步任务化后,接口立即返回 taskId,后台串行执行(不抬高
 * embedding/reranker 下游峰值)。任务状态存 Redis(带 TTL,不污染 DB),
 * 前端轮询 {@code GET /eval/tasks/{taskId}} 获取进度,完成后取 {@link #result}。</p>
 *
 * @author gj-llm
 */
@Data
public class RetrievalEvalTask {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    /** 任务 ID(UUID) */
    private String taskId;

    /** 知识库 ID */
    private Long datasetId;

    /** 状态:PENDING / RUNNING / COMPLETED / FAILED */
    private String status;

    /** 用例总数 */
    private int total;

    /** 已完成条数(进度 done/total) */
    private int done;

    /** 当前步骤描述,如"评测中 12/50" */
    private String currentStep;

    /** 失败原因(FAILED 时填) */
    private String errorMessage;

    /** 评测结果(COMPLETED 时填,进行中为 null) */
    private RetrievalEvalResult result;

    /** 创建时间戳(epoch millis) */
    private long createdAt;
}
