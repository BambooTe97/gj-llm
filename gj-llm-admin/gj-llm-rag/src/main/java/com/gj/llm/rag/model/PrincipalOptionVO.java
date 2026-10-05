package com.gj.llm.rag.model;

/**
 * 主体选择项（共享面板下拉）—— id + 展示名，由 base-admin 的用户 / 角色体系解析。
 *
 * @author gj-llm
 */
public record PrincipalOptionVO(Long id, String name) {
}
