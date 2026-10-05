package com.gj.llm.rag.service;

import com.gj.llm.common.util.JacksonUtils;
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
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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

    /** 正则兜底:LLM 输出大小写不可控,意图匹配不区分大小写 */
    private static final Pattern INTENT_RE =
            Pattern.compile("\"intent\"\\s*:\\s*\"(chat|retrieve)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern IDS_RE = Pattern.compile("\"dataset_ids\"\\s*:\\s*\\[([^\\]]*)\\]");
    /** 正则兜底用:sub_queries 数组体与其中带引号字符串(兜底仅支持字符串数组形态) */
    private static final Pattern SUB_QUERIES_RE = Pattern.compile("\"sub_queries\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern QUOTED_RE = Pattern.compile("\"([^\"]+)\"");

    private final ChatClient chatClient;
    private final RagProperties ragProperties;
    private final DatasetService datasetService;
    private final RedisService redisService;
    private final DatasetVisibleService datasetVisibleService;

    private static final String PLAN_PROMPT = """
            你是知识库检索路由助手。根据【知识库列表】和【用户问题】判断意图、选择知识库,并在问题复杂时拆分子问题。

            知识库列表:
            %s

            用户问题: %s

            只输出一行 JSON,不要输出任何其他内容,格式:
            {"intent": "chat或retrieve", "dataset_ids": ["id1", "id2"], "sub_queries": ["子问题1", "子问题2"]}

            规则:
            1. 问候、闲聊、与知识库内容无关的问题 -> intent 填 "chat",dataset_ids 填空数组,sub_queries 填空数组
            2. 需要查询资料才能回答 -> intent 填 "retrieve",并从列表选择最相关的知识库 id(最多 %d 个)
            3. 不确定选哪个时,优先选择描述最可能包含答案的知识库
            4. 当问题包含多个独立信息点(例如"A的配置方法,以及B是什么意思"),把每个信息点拆成一条独立子问题写入 sub_queries(最多 %d 条);每条子问题必须语义自含,不使用"它/该/上述"等指代词,不带问候或闲聊成分
            5. 单一简单问题 sub_queries 填空数组,不要强行拆分
            """;

    public QueryPlanner(ChatModel chatModel, RagProperties ragProperties,
                        DatasetService datasetService, RedisService redisService,
                        DatasetVisibleService datasetVisibleService) {
        this.chatClient = ChatClient.create(chatModel);
        this.ragProperties = ragProperties;
        this.datasetService = datasetService;
        this.redisService = redisService;
        this.datasetVisibleService = datasetVisibleService;
    }

    /**
     * 规划一次提问的路由决策(永不抛异常,失败走降级链)。
     *
     * <p>数据可见域在路由源头生效:候选库先与用户可见集求交集,后续扇出/LLM 选库/
     * 降级链全部基于交集结果 —— 用户不可见的库既不进 prompt 也不进检索
     * (userId 为 null 时可见集为空,只能闲聊,fail-closed)。</p>
     *
     * @param query  用户原始问题
     * @param userId 发起提问的用户 ID(由调用方线程解析后显式传入,不在此触碰 ThreadLocal)
     * @return 决策:intent=RETRIEVE 时 datasetIds 保证非空、全部存在且均在用户可见域内
     */
    public RoutingDecision plan(String query, Long userId) {
        List<DatasetBrief> known = cachedDatasets();
        if (known.isEmpty()) {
            // 无可用知识库,只能闲聊
            return RoutingDecision.chat();
        }

        // 可见域交集:候选库 -> 用户可见的库
        Set<Long> visible = datasetVisibleService.visibleDatasetIds(userId);
        List<DatasetBrief> allowed = known.stream()
                .filter(d -> visible.contains(d.id()))
                .collect(Collectors.toList());
        if (allowed.isEmpty()) {
            // 用户可见域内无可用知识库,只能闲聊
            return RoutingDecision.chat();
        }

        RagProperties.Routing cfg = ragProperties.getRouting();
        boolean fanout = allowed.size() <= cfg.getFanoutThreshold();
        PlanResult raw = callPlanner(query, allowed, cfg);

        // 规划失败/超时 -> 降级扇出(小库全量,大库按文档量取前 N),宁可多检索不可漏答
        if (raw == null) {
            List<DatasetBrief> picked = fanout ? allowed : topByDocCount(allowed, cfg.getFanoutThreshold());
            return decision(picked);
        }

        if ("chat".equals(raw.intent())) {
            return RoutingDecision.chat();
        }
        if (fanout) {
            // 多路召回模式:忽略 LLM 选库(避免小模型选错漏答),全库并发检索
            return decision(allowed, raw.subQueries());
        }

        // LLM 选库模式:id 存在性校验(防幻觉编号,同引用角标思路),无效剔除
        List<DatasetBrief> picked = allowed.stream()
                .filter(d -> raw.datasetIds().contains(d.id()))
                .limit(cfg.getMaxDatasets())
                .collect(Collectors.toList());
        if (picked.isEmpty()) {
            // 所选 id 全部无效(幻觉)-> 降级扇出
            picked = topByDocCount(allowed, cfg.getFanoutThreshold());
        }
        return decision(picked, raw.subQueries());
    }

    // ==================== LLM 规划调用 ====================

    /** 规划调用的原始解析结果(intent 归一为 chat/retrieve;subQueries 已归一化,未拆解为空列表);包级可见供测试 */
    record PlanResult(String intent, List<Long> datasetIds, List<String> subQueries) {
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
            String prompt = PLAN_PROMPT.formatted(list, query, cfg.getMaxDatasets(), cfg.getMaxSubQueries());

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> callLlm(prompt));
            String resp = future.get(cfg.getPlannerTimeoutMs(), TimeUnit.MILLISECONDS);
            if (resp == null || resp.isBlank()) {
                return null;
            }
            PlanResult parsed = parse(resp);
            if (parsed == null) {
                return null;
            }
            // 拆解归一化(开关关闭时丢弃,保证 flag 关 = 行为不变);不足 2 条视为未拆解
            List<String> subQueries = cfg.isDecompositionEnabled()
                    ? normalizeSubQueries(parsed.subQueries(), query, cfg.getMaxSubQueries())
                    : List.of();
            return new PlanResult(parsed.intent(), parsed.datasetIds(), subQueries);
        } catch (TimeoutException e) {
            log.warn("路由规划超时({}ms),走降级链: {}", cfg.getPlannerTimeoutMs(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("路由规划失败,走降级链: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 宽松解析:剥围栏后 Jackson 主路径,正则兜底;容忍代码围栏/前后缀文本,永不抛异常。
     * 包级可见供测试。
     */
    PlanResult parse(String resp) {
        // ① Jackson 主路径:截取首个 '{' 到最后一个 '}' 的 JSON 体(readTree 对非法输入会抛异常,此处必须自吞)
        String json = sliceJsonObject(resp);
        if (json != null) {
            try {
                JsonNode node = JacksonUtils.readTree(json);
                JsonNode intentNode = node.path("intent");
                if (intentNode.isTextual()) {
                    String intent = "chat".equalsIgnoreCase(intentNode.asText()) ? "chat" : "retrieve";
                    List<Long> ids = new ArrayList<>();
                    for (JsonNode idNode : node.path("dataset_ids")) {
                        long id = idNode.asLong(0);
                        if (id > 0) {
                            ids.add(id);
                        }
                    }
                    return new PlanResult(intent, ids, parseSubQueries(node.path("sub_queries")));
                }
            } catch (Exception e) {
                log.debug("规划输出 Jackson 解析失败,回退正则: {}", e.getMessage());
            }
        }

        // ② 正则兜底:提取 intent 与 dataset_ids(sub_queries 仅支持字符串数组形态)
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
        List<String> subQueries = new ArrayList<>();
        Matcher sm = SUB_QUERIES_RE.matcher(resp);
        if (sm.find()) {
            Matcher qm = QUOTED_RE.matcher(sm.group(1));
            while (qm.find()) {
                // 兜底容错:过滤对象形态 [{"q":...}] 里的 key 名
                if (!"q".equals(qm.group(1))) {
                    subQueries.add(qm.group(1));
                }
            }
        }
        return new PlanResult(intent, ids, subQueries);
    }

    /** 截取 JSON 对象体:首个 '{' 到最后一个 '}';不含完整对象时返回 null */
    private String sliceJsonObject(String resp) {
        if (resp == null) {
            return null;
        }
        int start = resp.indexOf('{');
        int end = resp.lastIndexOf('}');
        return (start >= 0 && end > start) ? resp.substring(start, end + 1) : null;
    }

    /** sub_queries 节点 -> 子问题列表;容忍 [".."] 与 [{"q":".."}] 两种形态 */
    private List<String> parseSubQueries(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode item : arr) {
            if (item.isTextual()) {
                out.add(item.asText());
            } else if (item.isObject()) {
                JsonNode q = item.path("q");
                if (q.isTextual() && !q.asText().isBlank()) {
                    out.add(q.asText());
                }
            }
        }
        return out;
    }

    /**
     * 归一化子问题:去空白、规范化去重(含互相包含)、去与原问题相同者、截断到 max;
     * 结果不足 2 条视为未拆解,返回空列表(单查询路径兜底)。包级可见供测试。
     */
    List<String> normalizeSubQueries(List<String> raw, String originalQuery, int max) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        String normOriginal = normalize(originalQuery);
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String item = s.trim();
            String norm = normalize(item);
            if (norm.isEmpty() || norm.equals(normOriginal)) {
                continue; // 与原问题相同:没有信息增量
            }
            boolean dup = false;
            for (String kept : out) {
                String normKept = normalize(kept);
                if (normKept.equals(norm) || normKept.contains(norm) || norm.contains(normKept)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                out.add(item);
            }
            if (out.size() >= max) {
                break;
            }
        }
        return out.size() >= 2 ? out : List.of();
    }

    /** 规范化比较键:小写、去所有空白与中英文标点 */
    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[\\s\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF]+", "");
    }

    /** 调 LLM(复用 QueryRewriter 的调用形态:独立小模型、关思考、限 token) */
    private String callLlm(String prompt) {
        String model = resolveModel();
        // numPredict 是上限非目标:开启拆解时放宽到 320 容纳子问题数组,短输出不受影响
        OllamaChatOptions.Builder options = OllamaChatOptions.builder()
                .numPredict(ragProperties.getRouting().isDecompositionEnabled() ? 320 : 128)
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

    /** 由库列表构建 RETRIEVE 决策(ids 与 names 同序,未拆解) */
    private RoutingDecision decision(List<DatasetBrief> picked) {
        return decision(picked, List.of());
    }

    /** 由库列表 + 拆解子问题构建 RETRIEVE 决策 */
    private RoutingDecision decision(List<DatasetBrief> picked, List<String> subQueries) {
        return new RoutingDecision(RoutingDecision.Intent.RETRIEVE,
                picked.stream().map(DatasetBrief::id).collect(Collectors.toList()),
                picked.stream().map(DatasetBrief::name).collect(Collectors.toList()),
                subQueries);
    }
}
