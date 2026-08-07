package com.gj.llm.rag.eval;

import java.util.List;

/**
 * 测评运行请求 -- 指定本次跑哪些用例。
 *
 * @param queryIds 用例 ID 列表;null/空 = 跑该库全部用例,非空 = 只跑指定的(选择性测评)
 * @author gj-llm
 */
public record EvalRunRequest(List<Long> queryIds) {
}
