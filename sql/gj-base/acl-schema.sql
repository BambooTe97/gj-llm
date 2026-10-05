-- ============================================================
-- 模块：gj-llm-auth
-- 表名：resource_acl
-- 用途：数据授权 —— 通用资源 ACL（库级可见域共享授权；
--       resource_type='file' 为文档级隔离预留，当前逻辑不启用）
-- ============================================================

-- 资源授权表（所有权记录在资源表 owner_id 列，本表只存"额外共享授权"）
CREATE TABLE IF NOT EXISTS resource_acl (
    id             BIGINT       NOT NULL COMMENT '主键（雪花算法 ID）',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户休眠字段（SaaS 启用前恒 0）',
    resource_type  VARCHAR(32)  NOT NULL COMMENT '资源类型：dataset（file 为文档级预留）',
    resource_id    BIGINT       NOT NULL COMMENT '资源 ID（如 dataset.id）',
    principal_type VARCHAR(16)  NOT NULL COMMENT '主体类型：user | role（dept 预留，用户体系暂无部门）',
    principal_id   BIGINT       NOT NULL COMMENT '主体 ID（sys_user.id / sys_role.id）',
    created_by     BIGINT       NULL COMMENT '授权人用户 ID',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_acl (resource_type, resource_id, principal_type, principal_id),
    KEY idx_resource (resource_type, resource_id),
    KEY idx_principal (principal_type, principal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源授权表（数据可见域共享授权）';
