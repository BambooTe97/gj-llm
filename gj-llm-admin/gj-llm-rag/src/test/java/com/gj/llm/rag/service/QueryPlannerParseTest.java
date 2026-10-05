package com.gj.llm.rag.service;

import com.gj.llm.common.spring.SpringUtils;
import com.gj.llm.rag.config.RagProperties;
import com.gj.llm.redis.service.RedisService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link QueryPlanner} 规划输出解析与子问题归一化测试(纯逻辑,不触 LLM/Redis)。
 *
 * <p>{@link SpringUtils#getBean(Class)} 依赖静态 beanFactory,JacksonUtils.readTree 会经由它取
 * ObjectMapper;纯单测无 Spring 上下文,须在 {@code @BeforeAll} 反射注入一个注册了
 * {@link JsonMapper} 的 beanFactory,否则 parse() 的 Jackson 主路径静默降级到正则兜底,
 * 主路径等于没被测到。</p>
 *
 * @author gj-llm
 */
class QueryPlannerParseTest {

    private final QueryPlanner planner = new QueryPlanner(
            mock(ChatModel.class), new RagProperties(), mock(DatasetService.class), mock(RedisService.class),
            mock(DatasetVisibleService.class));

    @BeforeAll
    static void initBeanFactory() throws Exception {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        bf.registerSingleton("objectMapper", JsonMapper.builder().build());
        Field field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, bf);
    }

    // ==================== parse:Jackson 主路径 ====================

    @Test
    void parse_plainJson() {
        QueryPlanner.PlanResult r = planner.parse(
                "{\"intent\": \"retrieve\", \"dataset_ids\": [\"1\", \"3\"], \"sub_queries\": [\"A的配置\", \"B的含义\"]}");
        assertThat(r).isNotNull();
        assertThat(r.intent()).isEqualTo("retrieve");
        assertThat(r.datasetIds()).containsExactly(1L, 3L);
        assertThat(r.subQueries()).containsExactly("A的配置", "B的含义");
    }

    @Test
    void parse_jsonWithTrailingProse_andInvalidIdDropped() {
        QueryPlanner.PlanResult r = planner.parse(
                "{\"intent\": \"retrieve\", \"dataset_ids\": [\"1\", \"x\"], \"sub_queries\": [\"A配置\"]} 以上是结果");
        assertThat(r).isNotNull();
        assertThat(r.intent()).isEqualTo("retrieve");
        assertThat(r.datasetIds()).containsExactly(1L); // 非数字 id 剔除
        assertThat(r.subQueries()).containsExactly("A配置");
    }

    @Test
    void parse_fencedJson() {
        QueryPlanner.PlanResult r = planner.parse(
                "好的\n```json\n{\"intent\": \"chat\", \"dataset_ids\": [], \"sub_queries\": []}\n```\n以上");
        assertThat(r).isNotNull();
        assertThat(r.intent()).isEqualTo("chat");
        assertThat(r.datasetIds()).isEmpty();
        assertThat(r.subQueries()).isEmpty();
    }

    @Test
    void parse_objectFormSubQueries_tolerated() {
        QueryPlanner.PlanResult r = planner.parse(
                "{\"intent\": \"retrieve\", \"dataset_ids\": [1], \"sub_queries\": [{\"q\": \"A配置\"}, {\"q\": \"B含义\"}]}");
        assertThat(r).isNotNull();
        assertThat(r.subQueries()).containsExactly("A配置", "B含义");
    }

    @Test
    void parse_intentNormalized_caseInsensitive_andMissingSubQueries() {
        QueryPlanner.PlanResult r = planner.parse("{\"intent\": \"RETRIEVE\", \"dataset_ids\": [\"1\"]}");
        assertThat(r).isNotNull();
        assertThat(r.intent()).isEqualTo("retrieve");
        assertThat(r.subQueries()).isEmpty(); // 缺 sub_queries 字段 -> 空
    }

    // ==================== parse:正则兜底 ====================

    @Test
    void parse_brokenJson_fallsBackToRegex() {
        // 无大括号的残缺输出:Jackson 主路径拿不到 JSON 体,回退正则(仅字符串数组形态)
        QueryPlanner.PlanResult r = planner.parse(
                "\"intent\": \"retrieve\", \"dataset_ids\": [\"1\", \"x\"], \"sub_queries\": [\"A配置\"]");
        assertThat(r).isNotNull();
        assertThat(r.intent()).isEqualTo("retrieve");
        assertThat(r.datasetIds()).containsExactly(1L);
        assertThat(r.subQueries()).containsExactly("A配置");
    }

    @Test
    void parse_noIntentAtAll_returnsNull() {
        assertThat(planner.parse("抱歉,我不知道你在说什么")).isNull();
    }

    // ==================== normalizeSubQueries ====================

    @Test
    void normalize_trimsDropsBlank_andDedups() {
        List<String> out = planner.normalizeSubQueries(
                List.of(" A配置 ", "", "  ", "A配置", "B的含义"), "问个问题", 3);
        assertThat(out).containsExactly("A配置", "B的含义");
    }

    @Test
    void normalize_dropsSameAsOriginal_andContainmentDup() {
        List<String> out = planner.normalizeSubQueries(
                List.of("请介绍公司的退货政策", "退货政策", "行业惯例"), "请介绍公司的退货政策", 3);
        // 与原题完全相同 -> 丢;被先出现的保留项包含 -> 丢(保留更精炼的先现项)
        assertThat(out).containsExactly("退货政策", "行业惯例");
    }

    @Test
    void normalize_capsAtMax() {
        List<String> out = planner.normalizeSubQueries(
                List.of("甲问题的答案", "乙问题的定义", "丙问题的区别", "丁问题的用法"), "原问题", 3);
        assertThat(out).hasSize(3);
    }

    @Test
    void normalize_singleOrEmpty_treatedAsUndecomposed() {
        assertThat(planner.normalizeSubQueries(List.of("只有一条"), "原问题", 3)).isEmpty();
        assertThat(planner.normalizeSubQueries(List.of("原问题"), "原问题", 3)).isEmpty();
        assertThat(planner.normalizeSubQueries(List.of(), "原问题", 3)).isEmpty();
        assertThat(planner.normalizeSubQueries(null, "原问题", 3)).isEmpty();
    }
}
