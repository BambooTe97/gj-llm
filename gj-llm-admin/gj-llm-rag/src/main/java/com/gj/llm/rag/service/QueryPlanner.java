package com.gj.llm.rag.service;

import com.gj.llm.rag.config.RagProperties;
import com.gj.llm.rag.model.RoutingDecision;
import com.gj.llm.redis.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 知识库路由规划器 -- 判定用户意图(闲聊/检索)并选择目标知识库。
 *
 * <p>调用方为 chat 层路由器(每问一次,轻量小输出);策略参考业界主流形态:</p>
 * <ul>
 *   <li>READY 库数 ≤ {@code routing.fanout-threshold}:<b>多路召回</b>(Dify Multiple Recall /
 *       OpenAI file_search 形态) -- 忽略 LLM 选库,全库并发检索,LLM 仅判意图,零路由风险</li>
 *   <li>库数超过阈值:<b>LLM 选库</b>(Dify N-to-1 泛化 / LlamaIndex multi-select 形态) --
 *       LLM 从库清单选出 ≤ max-datasets 个,id 经存在性校验(防幻觉)</li>
 * </ul>
 *
 * <p>查询改写(HyDE 等)不在此处 -- 归 {@link QueryRewriter},路由与改写职责分离、故障域隔离。</p>
 *
 * <p>库清单走 Redis 短缓存(60s 兜底 + 增删改主动失效,见 {@link #DATASET_CACHE_KEY}),
 * 多实例部署时天然共享一致。</p>
 *
 * <p>降级链(永不抛异常):规划超时/失败 -> 小库全量扇出 / 大库按文档量取前 N 扇出
 * -- 宁可多检索不可漏答,保功能不保算力。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
public class QueryPlanner {

    /** 库清单 Redis 缓存 key(dataset 增/改/删时由 DatasetServiceImpl 主动失效) */
    public static final String DATASET_CACHE_KEY = "rag:route:datasets";

    private static final Pattern INTENT_RE = Pattern.compile("\"intent\"\\s*:\\s*\"(chat|retrieve)\"");
    private static final Pattern IDS_RE = Pattern.compile("\"dataset_ids\"\\s*:\\s*\\[([^\\]]*)\\]");

    private final ChatClient chatClient;
    private final RagProperties ragProperties;
    private final DatasetService datasetService;
    private final RedisService redisService;

    private static final String PLAN_PROMPT = """
            你是知识库检索路由助手。根据【知识库列表】和【用户问题】判断意图并选择要检索的知识库。

            知识库列表:
            %s

            用户问题: %s

            只输出一行 JSON,不要输出任何其他内容,格式:
            {"intent": "chat或retrieve", "dataset_ids": ["id1", "id2"]}

            规则:
            1. 问候、闲聊、与知识库内容无关的问题 -> intent 填 "chat",dataset_ids 填空数组
            2. 需要查询资料才能回答 -> intent 填 "retrieve",并从列表选择最相关的知识库 id(最多 %d 个)
            3. 不确定选哪个时,优先选择描述最可能包含答案的知识库
            """;

    public QueryPlanner(ChatModel chatModel, RagProperties ragProperties,
                        DatasetService datasetService, RedisService redisService) {
        this.chatClient = ChatClient.create(chatModel);
        this.ragProperties = ragProperties;
        this.datasetService = datasetService;
        this.redisService = redisService;
    }

    /**
     * 规划一次提问的路由决策(永不抛异常,失败走降级链)。
     *
     * @param query 用户原始问题
     * @return 决策:intent=RETRIEVE 时 datasetIds 保证非空且全部存在
     */
    public RoutingDecision plan(String query) {
        List<DatasetBrief> known = cachedDatasets();
        if (known.isEmpty()) {
            // 无可用知识库,只能闲聊
            return RoutingDecision.chat();
        }

        RagProperties.Routing cfg = ragProperties.getRouting();
        boolean fanout = known.size() <= cfg.getFanoutThreshold();
        PlanResult raw = callPlanner(query, known, cfg);

        // 规划失败/超时 -> 降级扇出(小库全量,大库按文档量取前 N),宁可多检索不可漏答
        if (raw == null) {
            List<DatasetBrief> picked = fanout ? known : topByDocCount(known, cfg.getFanoutThreshold());
            return decision(picked);
        }

        if ("chat".equals(raw.intent())) {
            return RoutingDecision.chat();
        }
        if (fanout) {
            // 多路召回模式:忽略 LLM 选库(避免小模型选错漏答),全库并发检索
            return decision(known);
        }

        // LLM 选库模式:id 存在性校验(防幻觉编号,同引用角标思路),无效剔除
        List<DatasetBrief> picked = known.stream()
                .filter(d -> raw.datasetIds().contains(d.id()))
                .limit(cfg.getMaxDatasets())
                .collect(Collectors.toList());
        if (picked.isEmpty()) {
            // 所选 id 全部无效(幻觉)-> 降级扇出
            picked = topByDocCount(known, cfg.getFanoutThreshold());
        }
        return decision(picked);
    }

    // ==================== LLM 规划调用 ====================

    /** 规划调用的原始解析结果(intent 归一为 chat/retrieve) */
    private record PlanResult(String intent, List<Long> datasetIds) {
    }

    /**
     * 调 LLM 做规划,带超时保护。
     *
     * <p>超时后底层调用继续跑完但结果作废(仅浪费一次小调用,不阻塞主流程);
     * 超时/异常一律返回 null,由调用方走降级链。</p>
     */
    private PlanResult callPlanner(String query, List<DatasetBrief> datasets, RagProperties.Routing cfg) {
        try {
            String list = datasets.stream()
                    .map(d -> "- id: " + d.id() + ", 名称: " + d.name()
                            + ", 描述: " + (d.description() == null ? "无" : d.description()))
                    .collect(Collectors.joining("\n"));
            String prompt = PLAN_PROMPT.formatted(list, query, cfg.getMaxDatasets());

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> callLlm(prompt));
            String resp = future.get(cfg.getPlannerTimeoutMs(), TimeUnit.MILLISECONDS);
            if (resp == null || resp.isBlank()) {
                return null;
            }
            return parse(resp);
        } catch (TimeoutException e) {
            log.warn("路由规划超时({}ms),走降级链: {}", cfg.getPlannerTimeoutMs(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("路由规划失败,走降级链: {}", e.getMessage());
            return null;
        }
    }

    /** 宽松解析:容忍代码围栏/前后缀文本,正则提取 intent 与 dataset_ids */
    private PlanResult parse(String resp) {
        Matcher im = INTENT_RE.matcher(resp);
        String intent = im.find() ? im.group(1) : null;
        if (intent == null) {
            return null; // 连意图都解析不出,视为规划失败
        }

        List<Long> ids = new ArrayList<>();
        Matcher dm = IDS_RE.matcher(resp);
        if (dm.find()) {
            for (String part : dm.group(1).split(",")) {
                String cleaned = part.replace("\"", "").trim();
                if (cleaned.isEmpty()) {
                    continue;
                }
                try {
                    ids.add(Long.parseLong(cleaned));
                } catch (NumberFormatException ignored) {
                    // 非数字 id:幻觉内容,存在性校验会兜底剔除
                }
            }
        }
        return new PlanResult(intent, ids);
    }

    /** 调 LLM(复用 QueryRewriter 的调用形态:独立小模型、关思考、限 token) */
    private String callLlm(String prompt) {
        String model = resolveModel();
        OllamaChatOptions.Builder options = OllamaChatOptions.builder()
                .numPredict(128)
                .disableThinking(); // 路由判定不需要思考,省 token
        if (model != null && !model.isBlank()) {
            options.model(model);
        }
        return chatClient.prompt()
                .messages(new UserMessage(prompt))
                .options(options)
                .call()
                .content();
    }

    /** 规划模型:优先 routing.planner-model,未配置则复用 rewrite-model */
    private String resolveModel() {
        String model = ragProperties.getRouting().getPlannerModel();
        if (model == null || model.isBlank()) {
            model = ragProperties.getRewriteModel();
        }
        return model;
    }

    // ==================== 库清单缓存 ====================

    /**
     * 库清单精简视图(planner prompt 与 Redis 缓存共用,只带路由所需字段)。
     *
     * @param id          库 ID
     * @param name        库名
     * @param description 库描述
     * @param docCount    文档数量(降级链按文档量选库用)
     */
    public record DatasetBrief(Long id, String name, String description, Integer docCount) {
    }

    /** 读库清单:Redis 短缓存(兜底 TTL 60s),未命中或 Redis 不可用时回源 DB */
    private List<DatasetBrief> cachedDatasets() {
        RagProperties.Routing cfg = ragProperties.getRouting();
        try {
            List<DatasetBrief> cached = redisService.get(DATASET_CACHE_KEY, new TypeReference<>() {
            });
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("读取库清单缓存失败(回源 DB): {}", e.getMessage());
        }
        List<DatasetBrief> fresh = datasetService.listAll().stream()
                .filter(d -> "READY".equals(d.getStatus()))
                .map(d -> new DatasetBrief(d.getId(), d.getName(), d.getDescription(), d.getDocCount()))
                .collect(Collectors.toList());
        try {
            redisService.set(DATASET_CACHE_KEY, fresh, Duration.ofSeconds(cfg.getDatasetCacheTtlSeconds()));
        } catch (Exception e) {
            log.warn("写入库清单缓存失败(不影响本次路由): {}", e.getMessage());
        }
        return fresh;
    }

    // ==================== 辅助 ====================

    /** 按文档量降序取前 n 个库(降级链:文档量大的库更可能命中) */
    private List<DatasetBrief> topByDocCount(List<DatasetBrief> known, int n) {
        return known.stream()
                .sorted(Comparator.comparing((DatasetBrief d) -> d.docCount() != null ? d.docCount() : 0).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /** 由库列表构建 RETRIEVE 决策(ids 与 names 同序) */
    private RoutingDecision decision(List<DatasetBrief> picked) {
        return new RoutingDecision(RoutingDecision.Intent.RETRIEVE,
                picked.stream().map(DatasetBrief::id).collect(Collectors.toList()),
                picked.stream().map(DatasetBrief::name).collect(Collectors.toList()));
    }
}
