package com.gj.llm.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接口实体 -- 映射 {@code sys_api} 表（MyBatis-Plus）。
 *
 * <p>由 {@code ApiScanner} 在应用启动时扫描所有 Controller 的 {@code @RequestMapping}
 * 自动入库，无需手工维护。{@code isDeleted} 标记接口是否已从代码中移除（保留历史，
 * 不物理删除，避免角色关联悬空）。注意：字段刻意命名为 {@code isDeleted} 以避开
 * mybatis-plus 全局逻辑删除（{@code logic-delete-field=deleted}）的自动过滤，
 * 由扫描器手动管理。接口表为系统自动维护的元数据，不含审计字段。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_api")
public class ApiEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** Controller 全限定类名 */
    private String controller;

    /** Controller 方法名 */
    private String methodName;

    /** HTTP 方法：GET/POST/PUT/DELETE 等 */
    private String httpMethod;

    /** 接口路径（含路径变量，如 /api/users/{id}） */
    private String path;

    /** 接口描述 */
    private String summary;

    /** 删除标记：0=有效，1=接口已从代码移除 */
    @Builder.Default
    private Integer isDeleted = 0;
}
