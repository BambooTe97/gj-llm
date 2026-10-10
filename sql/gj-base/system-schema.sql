-- ============================================================
-- gj-base-admin 系统增强四件套 Schema
-- 部门管理 / 字典管理 / 参数配置 / 操作日志 / 登录日志 / 角色数据域
--
-- 幂等脚本：可重复执行（CREATE TABLE IF NOT EXISTS + INSERT IGNORE）
-- 菜单 ID 分配：C 行 2005-2010，B 行 25xx/26xx/27xx/2801/2901/3001，全局 B 行 4100/4101
-- 注意：本脚本需在应用启动【之前】执行 —— ApiAutoLinker 启动时按
--       sys_menu.perms 建立 sys_menu_api 权限链接。
-- ============================================================

-- ----------------------------
-- 1. 部门表（树形：parent_id 自引用 + ancestors 祖级链路）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_dept (
    id          BIGINT       NOT NULL                COMMENT '主键（应用侧雪花ID）',
    parent_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '父部门ID，0=顶级',
    ancestors   VARCHAR(500) NOT NULL DEFAULT ''     COMMENT '祖级列表，如 0,100',
    name        VARCHAR(50)  NOT NULL                COMMENT '部门名称',
    sort        INT          NOT NULL DEFAULT 0      COMMENT '显示顺序（越小越靠前）',
    leader      VARCHAR(50)  NULL                    COMMENT '负责人',
    phone       VARCHAR(20)  NULL                    COMMENT '联系电话',
    email       VARCHAR(100) NULL                    COMMENT '邮箱',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态（1=启用 0=停用）',
    create_by   VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '创建人',
    update_by   VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '更新人',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_dept_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

INSERT IGNORE INTO sys_dept (id, parent_id, ancestors, name, sort, status, create_by) VALUES
(100, 0,   '0',     '总公司', 1, 1, 'system'),
(101, 100, '0,100', '研发部', 1, 1, 'system'),
(102, 100, '0,100', '测试部', 2, 1, 'system');

-- ----------------------------
-- 2. 字典类型表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_dict_type (
    id          BIGINT       NOT NULL                COMMENT '主键（应用侧雪花ID）',
    name        VARCHAR(100) NOT NULL                COMMENT '字典名称',
    type        VARCHAR(100) NOT NULL                COMMENT '字典类型（唯一编码）',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态（1=启用 0=停用）',
    remark      VARCHAR(500) NULL                    COMMENT '备注',
    create_by   VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '创建人',
    update_by   VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '更新人',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

-- ----------------------------
-- 3. 字典数据表（dict_type 冗余存储，类型改名时级联更新）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_dict_data (
    id          BIGINT       NOT NULL                COMMENT '主键（应用侧雪花ID）',
    dict_type   VARCHAR(100) NOT NULL                COMMENT '所属字典类型编码',
    label       VARCHAR(100) NOT NULL                COMMENT '显示标签',
    dict_value  VARCHAR(100) NOT NULL                COMMENT '键值',
    sort        INT          NOT NULL DEFAULT 0      COMMENT '显示顺序（越小越靠前）',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态（1=启用 0=停用）',
    remark      VARCHAR(500) NULL                    COMMENT '备注',
    create_by   VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '创建人',
    update_by   VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '更新人',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type_value (dict_type, dict_value),
    KEY idx_dict_data_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';

INSERT IGNORE INTO sys_dict_type (id, name, type, status, remark, create_by) VALUES
(1, '通用状态', 'sys_status', 1, '系统内置示例字典', 'system');

INSERT IGNORE INTO sys_dict_data (id, dict_type, label, dict_value, sort, status, create_by) VALUES
(1, 'sys_status', '启用', '1', 1, 1, 'system'),
(2, 'sys_status', '禁用', '0', 2, 1, 'system');

-- ----------------------------
-- 4. 参数配置表（运行时可调参数，避免业务参数写死 application.yml）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_config (
    id           BIGINT       NOT NULL                COMMENT '主键（应用侧雪花ID）',
    name         VARCHAR(100) NOT NULL                COMMENT '参数名称',
    config_key   VARCHAR(100) NOT NULL                COMMENT '参数键名（唯一）',
    config_value VARCHAR(500) NOT NULL DEFAULT ''     COMMENT '参数键值',
    built_in     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否内置（1=内置不可删除 0=自定义）',
    remark       VARCHAR(500) NULL                    COMMENT '备注',
    create_by    VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '创建人',
    update_by    VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '更新人',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='参数配置表';

INSERT IGNORE INTO sys_config (id, name, config_key, config_value, built_in, remark, create_by) VALUES
(1, '系统名称', 'sys.name', 'gj-llm', 1, '系统内置参数', 'system');

-- ----------------------------
-- 5. 操作日志表（异步落库，无审计列，createdAt 由应用侧写入）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id             BIGINT        NOT NULL               COMMENT '主键（应用侧雪花ID）',
    module         VARCHAR(50)   NOT NULL               COMMENT '操作模块，如 用户管理',
    type           VARCHAR(50)   NOT NULL               COMMENT '操作类型，如 新增/删除/登录',
    method         VARCHAR(200)  NOT NULL DEFAULT ''    COMMENT '操作方法（类名.方法名）',
    request_uri    VARCHAR(255)  NOT NULL DEFAULT ''    COMMENT '请求 URI',
    request_method VARCHAR(10)   NOT NULL DEFAULT ''    COMMENT '请求方式（GET/POST/...）',
    operator       VARCHAR(50)   NOT NULL DEFAULT ''    COMMENT '操作人用户名',
    user_id        BIGINT        NULL                   COMMENT '操作人ID',
    ip             VARCHAR(64)   NOT NULL DEFAULT ''    COMMENT '客户端 IP',
    params         TEXT          NULL                   COMMENT '请求参数（JSON，敏感字段已脱敏）',
    result         TEXT          NULL                   COMMENT '返回结果（JSON，截断存储）',
    status         TINYINT       NOT NULL DEFAULT 1     COMMENT '操作状态（1=成功 0=失败）',
    error_msg      VARCHAR(1000) NULL                   COMMENT '错误消息（截断存储）',
    cost_ms        BIGINT        NOT NULL DEFAULT 0     COMMENT '耗时（毫秒）',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_oper_created (created_at),
    KEY idx_oper_operator (operator),
    KEY idx_oper_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ----------------------------
-- 6. sys_user 增加部门列（幂等 ALTER：MySQL 无 ADD COLUMN IF NOT EXISTS）
-- ----------------------------
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'dept_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN dept_id BIGINT NULL COMMENT ''所属部门ID'' AFTER email',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 7. 菜单种子：页面 C 行（父级 2000=系统管理目录，auth-schema.sql 已建）
-- ----------------------------
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
(2005, 2000, '部门管理', 'C', '/system/dept',   'system/dept/DeptManage',         'system:dept:list',   'OfficeBuilding', 1, 1, 1, 'system'),
(2006, 2000, '字典管理', 'C', '/system/dict',   'system/dict/DictManage',         'system:dict:list',   'Collection',     5, 1, 1, 'system'),
(2007, 2000, '参数配置', 'C', '/system/config', 'system/config/ConfigManage',     'system:config:list', 'Tools',          6, 1, 1, 'system'),
(2008, 2000, '操作日志', 'C', '/system/log',    'system/log/OperLogManage',       'system:log:list',    'Document',       8, 1, 1, 'system'),
(2009, 2000, '在线用户', 'C', '/system/online', 'system/online/OnlineUserManage', 'system:online:list', 'Monitor',        9, 1, 1, 'system');

-- ----------------------------
-- 8. 菜单种子：按钮 B 行（visible=0，B 行不进导航，仅作权限点）
-- ----------------------------
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, perms, sort, visible, status, create_by) VALUES
(2501, 2005, '部门新增', 'B', 'system:dept:add',    1, 0, 1, 'system'),
(2502, 2005, '部门编辑', 'B', 'system:dept:edit',   2, 0, 1, 'system'),
(2503, 2005, '部门删除', 'B', 'system:dept:remove', 3, 0, 1, 'system'),
(2601, 2006, '字典新增', 'B', 'system:dict:add',    1, 0, 1, 'system'),
(2602, 2006, '字典编辑', 'B', 'system:dict:edit',   2, 0, 1, 'system'),
(2603, 2006, '字典删除', 'B', 'system:dict:remove', 3, 0, 1, 'system'),
(2701, 2007, '参数新增', 'B', 'system:config:add',    1, 0, 1, 'system'),
(2702, 2007, '参数编辑', 'B', 'system:config:edit',   2, 0, 1, 'system'),
(2703, 2007, '参数删除', 'B', 'system:config:remove', 3, 0, 1, 'system'),
(2801, 2008, '日志清空', 'B', 'system:log:clear',     1, 0, 1, 'system'),
(2901, 2009, '强制下线', 'B', 'system:online:forceLogout', 1, 0, 1, 'system');

-- ----------------------------
-- 9. 菜单种子：全局权限点 B 行（parent=0，任何页面消费；授予 USER 角色）
-- ----------------------------
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
(4100, 0, '字典查询', 'B', NULL, NULL, 'system:dict:data',  NULL, 10, 0, 1, 'system'),
(4101, 0, '参数查询', 'B', NULL, NULL, 'system:config:key', NULL, 11, 0, 1, 'system');

-- ----------------------------
-- 10. 角色授权：ADMIN(1) 全量；USER(2) 仅全局消费权限点（管理页面不出现）
-- ----------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu
WHERE id BETWEEN 2005 AND 2009
   OR id BETWEEN 2501 AND 2503
   OR id BETWEEN 2601 AND 2603
   OR id BETWEEN 2701 AND 2703
   OR id IN (2801, 2901, 4100, 4101);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(2, 4100),
(2, 4101);

-- ----------------------------
-- 11. 登录日志表（异步落库，记录每次登录尝试的成功/失败；userId 可空——失败尝试的用户可能不存在）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_logininfor (
    id         BIGINT       NOT NULL                COMMENT '主键（应用侧雪花ID）',
    username   VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '登录账号（尝试值，可能不存在）',
    user_id    BIGINT       NULL                    COMMENT '用户ID（可空：失败尝试的用户可能不存在）',
    ip         VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '客户端 IP',
    browser    VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '浏览器',
    os         VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '操作系统',
    status     TINYINT      NOT NULL DEFAULT 0      COMMENT '登录状态（1=成功 0=失败）',
    msg        VARCHAR(255) NOT NULL DEFAULT ''     COMMENT '提示消息（登录成功/用户名或密码错误/账号锁定中...）',
    login_time DATETIME     NOT NULL                COMMENT '登录时间（应用侧写入）',
    PRIMARY KEY (id),
    KEY idx_login_time (login_time),
    KEY idx_login_username (username),
    KEY idx_login_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- ----------------------------
-- 12. 菜单种子：登录日志 C 行（2010）+ 日志清空 B 行（3001）
-- ----------------------------
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
(2010, 2000, '登录日志', 'C', '/system/login-log', 'system/log/LoginLogManage', 'system:loginlog:list', 'Key', 10, 1, 1, 'system');

INSERT IGNORE INTO sys_menu (id, parent_id, name, type, perms, sort, visible, status, create_by) VALUES
(3001, 2010, '日志清空', 'B', 'system:loginlog:clear', 1, 0, 1, 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 2010),
(1, 3001);

-- ----------------------------
-- 13. 数据权限：sys_role 增加数据范围列（幂等 ALTER）+ 角色自定义部门关联表
--     data_scope 默认 1=全部，存量角色行为不变；2=自定义（生效范围见 sys_role_dept）
-- ----------------------------
SET @scope_col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'data_scope');
SET @scope_ddl = IF(@scope_col_exists = 0,
    'ALTER TABLE sys_role ADD COLUMN data_scope TINYINT NOT NULL DEFAULT 1 COMMENT ''数据范围（1=全部 2=自定义 3=本部门 4=本部门及以下 5=仅本人）''',
    'SELECT 1');
PREPARE scope_stmt FROM @scope_ddl;
EXECUTE scope_stmt;
DEALLOCATE PREPARE scope_stmt;

CREATE TABLE IF NOT EXISTS sys_role_dept (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    dept_id BIGINT NOT NULL COMMENT '部门ID（data_scope=2 自定义范围生效）',
    PRIMARY KEY (role_id, dept_id),
    KEY idx_role_dept_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色自定义数据域-部门关联表';

-- ----------------------------
-- 14. 日志中心合并：操作日志（2008）与登录日志（2010）合并为"日志管理"单页（Tab 切换）
--     2008 更名并指向 LogCenter；2010 由 C 行转 B 行（仅作权限点，不再生成路由），
--     perms 均保留原值，role_menu 既有关联继续生效
-- ----------------------------
UPDATE sys_menu SET
    name = '日志管理',
    component = 'system/log/LogCenter'
WHERE id = 2008;

UPDATE sys_menu SET
    type = 'B',
    parent_id = 2008,
    visible = 0,
    path = NULL,
    component = NULL,
    icon = NULL,
    sort = 1
WHERE id = 2010;

