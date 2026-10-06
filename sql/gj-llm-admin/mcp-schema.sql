-- ============================================================
-- 模块：gj-llm-admin / gj-llm-mcp
-- 表名：mcp_api_key / mcp_server_config / mcp_audit_log
-- 用途：MCP 能力 —— API Key 认证、外部 MCP Server 连接配置、工具调用审计
-- 设计文档：gj-llm-admin/gj-llm-mcp/MCP_SYSTEM_ARCHITECTURE.md
-- ============================================================

-- API Key 表（MCP Server 对外认证：key 明文仅发放时返回一次，库内只存哈希）
CREATE TABLE IF NOT EXISTS mcp_api_key (
    id               BIGINT        COMMENT '主键（雪花算法 ID）',
    user_id          BIGINT        NOT NULL COMMENT '绑定用户 ID（可见域判定主体）',
    username         VARCHAR(64)   NULL COMMENT '用户名快照（审计展示用，免跨模块联查）',
    name             VARCHAR(64)   NOT NULL COMMENT 'Key 用途名称',
    key_prefix       VARCHAR(16)   NOT NULL COMMENT 'Key 前缀明文（辨识用，如 mcp_Ab3dEf）',
    key_hash         VARCHAR(64)   NOT NULL COMMENT 'SHA-256 十六进制哈希（不存明文）',
    status           TINYINT       DEFAULT 1 COMMENT '状态：1=启用, 0=停用',
    expires_at       DATETIME      NULL COMMENT '过期时间（NULL=永不过期）',
    last_used_at     DATETIME      NULL COMMENT '最近使用时间',
    create_by        VARCHAR(64)   NULL COMMENT '创建人',
    update_by        VARCHAR(64)   NULL COMMENT '更新人',
    created_at       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_mcp_api_key_hash (key_hash),
    INDEX idx_mcp_api_key_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP API Key 表';

-- 外部 MCP Server 连接配置表（DB 驱动动态管理：CRUD/启停运行时生效）
CREATE TABLE IF NOT EXISTS mcp_server_config (
    id               BIGINT        COMMENT '主键（雪花算法 ID）',
    name             VARCHAR(64)   NOT NULL COMMENT '连接标识（唯一）',
    transport        VARCHAR(20)   NOT NULL DEFAULT 'STREAMABLE_HTTP' COMMENT '传输类型：STREAMABLE_HTTP, SSE',
    endpoint         VARCHAR(512)  NOT NULL COMMENT '完整 URL（如 http://host:port/mcp）',
    auth_header_name  VARCHAR(64)  NULL COMMENT '认证头名（如 Authorization）',
    auth_header_value VARCHAR(1024) NULL COMMENT '认证头值（AES-GCM 加密存储，接口回显掩码）',
    enabled          TINYINT       DEFAULT 1 COMMENT '是否启用：1=启用, 0=停用（启停运行时生效）',
    health_status    VARCHAR(16)   DEFAULT 'UNKNOWN' COMMENT '健康状态：UP, DOWN, UNKNOWN',
    last_healthy_at  DATETIME      NULL COMMENT '最近健康时间',
    tool_count       INT           DEFAULT 0 COMMENT '工具数量（最近一次发现）',
    remark           VARCHAR(255)  NULL COMMENT '备注',
    create_by        VARCHAR(64)   NULL COMMENT '创建人',
    update_by        VARCHAR(64)   NULL COMMENT '更新人',
    created_at       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_mcp_server_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部 MCP Server 连接配置表';

-- 工具调用审计日志表（双向：SERVER_CALLED=server 被调, CLIENT_CALL=client 出调；异步落库）
CREATE TABLE IF NOT EXISTS mcp_audit_log (
    id               BIGINT        COMMENT '主键（雪花算法 ID）',
    direction        VARCHAR(16)   NOT NULL COMMENT '调用方向：SERVER_CALLED, CLIENT_CALL',
    user_id          BIGINT        NULL COMMENT '调用方用户 ID',
    username         VARCHAR(64)   NULL COMMENT '调用方用户名（快照）',
    api_key_id       BIGINT        NULL COMMENT 'server 被调时的 API Key ID',
    server_name      VARCHAR(64)   NULL COMMENT 'client 出调时的目标 server 名',
    tool_name        VARCHAR(128)  NOT NULL COMMENT '工具名',
    params_digest    VARCHAR(1024) NULL COMMENT '入参摘要（超长截断）',
    result_status    VARCHAR(16)   NOT NULL COMMENT '结果状态：SUCCESS, ERROR, TIMEOUT',
    error_message    VARCHAR(512)  NULL COMMENT '失败原因',
    cost_ms          BIGINT        DEFAULT 0 COMMENT '耗时（毫秒）',
    client_ip        VARCHAR(64)   NULL COMMENT '来源 IP',
    created_at       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_audit_time (created_at),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_tool (tool_name),
    INDEX idx_audit_status (result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 工具调用审计日志表';

-- ============================================================
-- 菜单与权限点（3xxx 段；接口由 ApiScanner 自动扫入 sys_api，
-- sys_menu_api 关联由 ApiAutoLinker RULES 启动自动建立，此处无需手工插）
-- ============================================================

-- 一级目录：MCP 管理（目录须带 SystemLayout 组件，动态路由以它作嵌套布局，同系统管理）
-- sort=3：与系统管理(2000)对调，MCP 管理排在系统管理之前
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
    (3000, 0, 'MCP管理', 'M', '/mcp', 'system/SystemLayout', NULL, 'Connection', 3, 1, 1, 'system');
-- 幂等修复：对已执行过旧脚本的库对齐——
-- 1) 早期版本该行 component 为 NULL 会导致动态路由整目录跳过（页面 404）
-- 2) 早期版本 sort=4 排在系统管理右侧，现与系统管理(2000)对调位置
UPDATE sys_menu SET component = 'system/SystemLayout' WHERE id = 3000 AND (component IS NULL OR component = '');
UPDATE sys_menu SET sort = 3 WHERE id = 3000 AND sort = 4;
UPDATE sys_menu SET sort = 4 WHERE id = 2000 AND sort = 3;

-- API Key 管理（C 型菜单 + B 型按钮）
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
    (3001, 3000, 'API Key管理', 'C', '/mcp/api-key', 'mcp/ApiKeyManage', 'mcp:key:list', 'Key', 1, 1, 1, 'system');
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, perms, sort, visible, status, create_by) VALUES
    (3101, 3001, 'Key发放', 'B', 'mcp:key:create', 1, 0, 1, 'system'),
    (3102, 3001, 'Key编辑', 'B', 'mcp:key:edit', 2, 0, 1, 'system'),
    (3103, 3001, 'Key删除', 'B', 'mcp:key:remove', 3, 0, 1, 'system');

-- 外部 MCP Server 管理
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
    (3002, 3000, '外部服务管理', 'C', '/mcp/server', 'mcp/ServerManage', 'mcp:server:list', 'Link', 2, 1, 1, 'system');
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, perms, sort, visible, status, create_by) VALUES
    (3201, 3002, '服务新增', 'B', 'mcp:server:create', 1, 0, 1, 'system'),
    (3202, 3002, '服务编辑', 'B', 'mcp:server:edit', 2, 0, 1, 'system'),
    (3203, 3002, '服务删除', 'B', 'mcp:server:remove', 3, 0, 1, 'system');

-- 审计日志查询
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
    (3003, 3000, '审计日志', 'C', '/mcp/audit', 'mcp/AuditLog', 'mcp:audit:view', 'Document', 3, 1, 1, 'system');

-- 授予 ADMIN 角色（固定 id=1）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 3000 AND 3999;
