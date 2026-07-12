-- ============================================================
-- 模块：gj-llm-chat
-- 表名：chat_conversation / chat_message
-- 用途：AI 对话管理 —— 会话记录、历史消息持久化
-- ============================================================

-- 对话会话表
CREATE TABLE IF NOT EXISTS chat_conversation (
    id            BIGINT       COMMENT '主键（雪花算法 ID）',
    title         VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    dataset_id    BIGINT       NULL COMMENT '关联的知识库 ID（NULL 表示通用对话）',
    user_id       BIGINT       NOT NULL COMMENT '创建用户 ID',
    message_count INT          DEFAULT 0 COMMENT '消息数量',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted    TINYINT      DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- 对话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id               BIGINT       COMMENT '主键（雪花算法 ID）',
    conversation_id  BIGINT       NOT NULL COMMENT '关联的会话 ID（chat_conversation.id）',
    role             VARCHAR(20)  NOT NULL COMMENT '角色：user / assistant / system',
    content          TEXT         NOT NULL COMMENT '消息内容',
    thinking         TEXT         NULL COMMENT '模型思考内容（reasoning / thinking）',
    metadata_json    JSON         NULL COMMENT '扩展元数据（引用片段、token 数、模型信息等）',
    created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';
