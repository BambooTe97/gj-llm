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
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
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
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '角色ID',
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
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ============================================================
-- 4. 初始化数据
-- ============================================================

-- 默认角色
INSERT IGNORE INTO sys_role (name, code, description) VALUES
    ('系统管理员', 'ADMIN', '系统最高权限，可管理用户和角色'),
    ('普通用户',   'USER',  '基础权限，可使用聊天功能');

-- ============================================================
-- 默认管理员账户
-- 用户名: admin  密码: 111111
-- ============================================================
INSERT IGNORE INTO sys_user (username, password, nickname, status) VALUES
    ('admin', '$2b$10$s/CmVaNV0WlFuSEsYvhZp.vzBNn9Mi7/njhCal4T8kMlXfxs7HkFe', '系统管理员', 1);

-- 为管理员分配 ADMIN 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id)
    SELECT u.id, r.id FROM sys_user u, sys_role r
    WHERE u.username = 'admin' AND r.code = 'ADMIN';
