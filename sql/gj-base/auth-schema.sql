-- ============================================================
-- gj-llm 数据库初始化脚本
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- ============================================================

-- 创建数据库（如尚未创建）
CREATE DATABASE IF NOT EXISTS gj_llm
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE gj_llm;

-- ============================================================
-- 1. 系统用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT          NOT NULL                COMMENT '用户ID（雪花算法）',
    username    VARCHAR(50)     NOT NULL                 COMMENT '用户名（登录凭证）',
    password    VARCHAR(200)    NOT NULL                 COMMENT '密码（BCrypt 密文）',
    nickname    VARCHAR(50)     DEFAULT NULL             COMMENT '昵称',
    avatar      VARCHAR(500)    DEFAULT NULL             COMMENT '头像URL',
    email       VARCHAR(100)    DEFAULT NULL             COMMENT '邮箱',
    status      TINYINT         NOT NULL DEFAULT 1       COMMENT '账户状态: 1=启用, 0=禁用',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- ============================================================
-- 2. 系统角色表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT          NOT NULL                COMMENT '角色ID（雪花算法）',
    name        VARCHAR(50)     NOT NULL                 COMMENT '角色名称（展示用）',
    code        VARCHAR(50)     NOT NULL                 COMMENT '角色编码（权限判断用，如 ADMIN、USER）',
    description VARCHAR(200)    DEFAULT NULL             COMMENT '角色描述',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- ============================================================
-- 3. 用户角色关联表（多对多）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ============================================================
-- 4. 初始化数据
-- ============================================================

-- 默认角色（固定 ID）
INSERT IGNORE INTO sys_role (id, name, code, description) VALUES
    (1, '系统管理员', 'ADMIN', '系统最高权限，可管理用户和角色'),
    (2, '普通用户',   'USER',  '基础权限，可使用聊天功能');

-- ============================================================
-- 默认管理员账户
-- 用户名: admin  密码: 111111
-- ============================================================
INSERT IGNORE INTO sys_user (id, username, password, nickname, status) VALUES
    (1, 'admin', '$2b$10$s/CmVaNV0WlFuSEsYvhZp.vzBNn9Mi7/njhCal4T8kMlXfxs7HkFe', '系统管理员', 1);

-- 为管理员分配 ADMIN 角色（固定关联 ID）
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- ============================================================
-- 1. 系统菜单表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGINT          NOT NULL                COMMENT '菜单ID（雪花算法，初始化用固定值）',
    parent_id   BIGINT          NOT NULL DEFAULT 0      COMMENT '父菜单ID，0=顶层',
    name        VARCHAR(50)     NOT NULL                COMMENT '菜单名称（展示用）',
    type        CHAR(1)         NOT NULL                COMMENT '菜单类型: M=目录, C=菜单, B=按钮',
    path        VARCHAR(200)    DEFAULT NULL            COMMENT '路由路径（按钮可为空）',
    component   VARCHAR(200)    DEFAULT NULL            COMMENT '前端组件路径（相对 views，如 system/user/UserManage；目录/按钮可空）',
    perms       VARCHAR(100)    DEFAULT NULL            COMMENT '权限标识（如 system:user:list）',
    icon        VARCHAR(100)    DEFAULT NULL            COMMENT '图标名称（Element Plus 图标组件名）',
    sort        INT             NOT NULL DEFAULT 0      COMMENT '排序（升序）',
    visible     TINYINT         NOT NULL DEFAULT 1      COMMENT '是否显示: 1=显示, 0=隐藏（隐藏仍注册路由）',
    status      TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1=启用, 0=禁用',
    create_by   VARCHAR(50)     DEFAULT NULL            COMMENT '创建者',
    update_by   VARCHAR(50)     DEFAULT NULL            COMMENT '更新者',
    created_at  DATETIME        COMMENT '创建时间',
    updated_at  DATETIME        COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单表';

-- ============================================================
-- 2. 角色-菜单关联表（多对多）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id),
    KEY idx_role_id (role_id),
    KEY idx_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';

-- ============================================================
-- 3. 初始化菜单数据
-- 菜单ID约定: 1xxx=业务菜单, 2xxx=系统管理
-- ============================================================

-- ---- 顶层业务菜单 ----
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
    (1000, 0,    '聊天',     'C', '/chat',     'chat/ChatView',           'chat:view',     'ChatDotRound', 1, 1, 1, 'system'),
    (1001, 0,    '知识库',   'C', '/datasets', 'dataset/DatasetListView', 'dataset:view',  'Collection',   2, 1, 1, 'system');

-- ---- 知识库按钮权限 ----
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, perms, sort, visible, status, create_by) VALUES
    (1101, 1001, '知识库新增', 'B', 'dataset:create', 1, 0, 1, 'system'),
    (1102, 1001, '知识库编辑', 'B', 'dataset:edit',   2, 0, 1, 'system'),
    (1103, 1001, '知识库删除', 'B', 'dataset:delete', 3, 0, 1, 'system');

-- ---- 系统管理目录 ----
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
    (2000, 0, '系统管理', 'M', '/system', 'system/SystemLayout', NULL, 'Setting', 3, 1, 1, 'system');

-- ---- 用户管理 ----
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
    (2001, 2000, '用户管理', 'C', '/system/user', 'system/user/UserManage', 'system:user:list', 'User', 1, 1, 1, 'system');
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, perms, sort, visible, status, create_by) VALUES
    (2101, 2001, '用户新增',     'B', 'system:user:add',       1, 0, 1, 'system'),
    (2102, 2001, '用户编辑',     'B', 'system:user:edit',      2, 0, 1, 'system'),
    (2103, 2001, '用户删除',     'B', 'system:user:remove',    3, 0, 1, 'system'),
    (2104, 2001, '重置密码',     'B', 'system:user:resetPwd',  4, 0, 1, 'system');

-- ---- 角色管理 ----
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
    (2002, 2000, '角色管理', 'C', '/system/role', 'system/role/RoleManage', 'system:role:list', 'UserFilled', 2, 1, 1, 'system');
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, perms, sort, visible, status, create_by) VALUES
    (2201, 2002, '角色新增', 'B', 'system:role:add',    1, 0, 1, 'system'),
    (2202, 2002, '角色编辑', 'B', 'system:role:edit',   2, 0, 1, 'system'),
    (2203, 2002, '角色删除', 'B', 'system:role:remove', 3, 0, 1, 'system');

-- ---- 菜单管理 ----
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
    (2003, 2000, '菜单管理', 'C', '/system/menu', 'system/menu/MenuManage', 'system:menu:list', 'Menu', 3, 1, 1, 'system');
INSERT IGNORE INTO sys_menu (id, parent_id, name, type, perms, sort, visible, status, create_by) VALUES
    (2301, 2003, '菜单新增', 'B', 'system:menu:add',    1, 0, 1, 'system'),
    (2302, 2003, '菜单编辑', 'B', 'system:menu:edit',   2, 0, 1, 'system'),
    (2303, 2003, '菜单删除', 'B', 'system:menu:remove', 3, 0, 1, 'system');

-- ============================================================
-- 4. 角色-菜单分配
-- ============================================================

-- ADMIN 角色（id=1）分配全部菜单
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- USER 角色（id=2）分配聊天 + 知识库（含知识库按钮，不含系统管理）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (2, 1000),
    (2, 1001),
    (2, 1101),
    (2, 1102),
    (2, 1103);

-- ============================================================
-- 1. 系统接口表（启动时由 ApiScanner 自动扫描入库，无需手工维护）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_api (
    id          BIGINT          NOT NULL                COMMENT '接口ID（雪花算法）',
    controller  VARCHAR(255)    NOT NULL                COMMENT 'Controller 全限定类名',
    method_name VARCHAR(100)    NOT NULL                COMMENT 'Controller 方法名',
    http_method VARCHAR(10)     NOT NULL                COMMENT 'HTTP 方法: GET/POST/PUT/DELETE 等',
    path        VARCHAR(255)    NOT NULL                COMMENT '接口路径（含路径变量，如 /api/users/{id}）',
    summary     VARCHAR(255)    DEFAULT NULL            COMMENT '接口描述',
    is_deleted  TINYINT         NOT NULL DEFAULT 0      COMMENT '删除标记: 0=有效, 1=接口已移除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_controller_method (controller, method_name),
    KEY idx_path (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统接口表';

-- ============================================================
-- 2. 菜单按钮-接口关联表
--    建立权限点（菜单按钮）与接口的多对多关系：
--    角色分配菜单按钮 -> 按钮关联接口 -> 用户可访问接口
--    启动时由 ApiAutoLinker 按约定建立默认关联，admin 可在菜单管理页调整。
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_menu_api (
    menu_id BIGINT NOT NULL COMMENT '菜单/按钮ID（权限点）',
    api_id  BIGINT NOT NULL COMMENT '接口ID',
    PRIMARY KEY (menu_id, api_id),
    KEY idx_api_id (api_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单按钮接口关联表';
