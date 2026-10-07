-- ============================================================
-- 模块：gj-base-admin（消息通知，随 RBAC/认证同居基座管理模块）
-- 表名：notify_message
-- 用途：消息通知 —— gj-netty 长连接基座的第一个业务消费者
--       （落库为事实源，WS 推送仅为实时提醒，离线由 REST 列表兜底）
-- ============================================================

CREATE TABLE IF NOT EXISTS notify_message (
    id          BIGINT       COMMENT '主键（雪花算法 ID）',
    user_id     BIGINT       NOT NULL COMMENT '接收用户 ID',
    title       VARCHAR(128) NOT NULL COMMENT '通知标题',
    content     VARCHAR(1024) NOT NULL COMMENT '通知内容',
    level       VARCHAR(16)  NOT NULL DEFAULT 'info' COMMENT '级别：info, success, warning, error',
    read_flag   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读, 1=已读',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    read_at     DATETIME     NULL COMMENT '阅读时间（未读为 NULL）',
    PRIMARY KEY (id),
    INDEX idx_notify_user (user_id, read_flag),
    INDEX idx_notify_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知消息表';

-- ============================================================
-- 权限点（4xxx 段；接口由 ApiScanner 自动扫入 sys_api，
-- sys_menu_api 关联由 ApiAutoLinker RULES 中 NotifyController -> notify:view 启动自动建立）
--
-- 说明：通知是全局头部能力（铃铛），无独立页面宿主，故用根级 B 型按钮行。
-- 安全性：MenuServiceImpl.getCurrentUserMenuTree 过滤 B 型，根级 B 行只进
-- permissions，不会污染前端导航树。
-- ============================================================

INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
    (4001, 0, '消息通知', 'B', NULL, NULL, 'notify:view', 'Bell', 9, 0, 1, 'system');

-- 授予 ADMIN 角色（固定 id=1）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 4001;

-- ============================================================
-- 通知管理中心（系统管理页）：C 型菜单挂系统管理(2000)下，
-- 管理权限点 notify:manage 与接收端 notify:view 分离
-- （普通用户只见铃铛，管理者才有发送/治理入口）
-- ============================================================

INSERT IGNORE INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, status, create_by) VALUES
    (2004, 2000, '通知管理', 'C', '/system/notify', 'system/notify/NotifyManage', 'notify:manage', 'Bell', 4, 1, 1, 'system');

-- 授予 ADMIN 角色（固定 id=1）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 2004;
