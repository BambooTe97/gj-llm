package com.gj.llm.netty;

import com.gj.llm.common.util.JacksonUtils;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;

/**
 * 单测环境支撑 —— gj-netty 单测无 Spring 上下文，{@code JacksonUtils} 内部的
 * {@code SpringUtils.getBean} 会因 beanFactory 未注入而 NPE。
 *
 * <p>按项目既定做法向 {@code JacksonUtils} 的静态 mapper 字段反射注入，
 * 配置用 {@link JacksonUtils#enhanceObjectMapper}（与生产同构；Long→String 由
 * JacksonConfig 在容器内追加，信封整型字段走 NumberSerializer 不受影响）。</p>
 *
 * @author gj-llm
 */
public final class TestSupport {

    private TestSupport() {
    }

    /** 在涉及 JSON 编解码的测试类 {@code @BeforeAll} 中调用（幂等） */
    public static void initJackson() {
        try {
            Field field = JacksonUtils.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            if (field.get(null) == null) {
                field.set(null, JacksonUtils.enhanceObjectMapper(JsonMapper.builder().build()));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("向 JacksonUtils 注入测试 mapper 失败", e);
        }
    }
}
