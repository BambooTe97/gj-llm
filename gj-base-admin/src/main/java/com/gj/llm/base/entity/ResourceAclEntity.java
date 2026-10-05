package com.gj.llm.base.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资源授权实体 —— 映射 {@code resource_acl} 表。
 *
 * <p>通用资源 ACL：资源所有权记录在资源表自身的 {@code owner_id} 列，
 * 本表只存"额外共享授权"（把资源共享给某用户/角色）。文档级隔离通过
 * {@code resource_type='file'} 预留，当前逻辑不启用。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("resource_acl")
public class ResourceAclEntity {

    /** 资源类型：知识库 */
    public static final String RESOURCE_TYPE_DATASET = "dataset";

    /** 资源类型：文档（文档级隔离预留，当前不启用） */
    public static final String RESOURCE_TYPE_FILE = "file";

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户休眠字段（SaaS 启用前恒 0） */
    @Builder.Default
    private Long tenantId = 0L;

    /** 资源类型：dataset | file（预留） */
    private String resourceType;

    /** 资源 ID */
    private Long resourceId;

    /** 主体类型：user | role（dept 预留） */
    private String principalType;

    /** 主体 ID（sys_user.id / sys_role.id） */
    private Long principalId;

    /** 授权人用户 ID */
    private Long createdBy;

    /** 授权时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
