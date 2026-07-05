-- ============================================================
-- 模块：gj-file
-- 表名：file_record（文件上传记录表）
-- 主键：BIGINT，雪花算法 ID（MyBatis-Plus ASSIGN_ID 自动生成）
-- ============================================================
CREATE TABLE IF NOT EXISTS file_record (
    id            BIGINT        NOT NULL COMMENT '主键（雪花算法 ID）',
    original_name VARCHAR(255)  NULL COMMENT '原始文件名',
    stored_name   VARCHAR(255)  NULL COMMENT '存储文件名（UUID 重命名）',
    extension     VARCHAR(50)   NULL COMMENT '文件扩展名（小写，不含点号）',
    size          BIGINT        COMMENT '文件大小（字节）',
    content_type  VARCHAR(255)  NULL COMMENT 'MIME 类型',
    file_path     VARCHAR(500)  COMMENT '相对上传目录的文件路径（如 yyyy/MM/dd/storedName）',
    create_by     VARCHAR(100)  NULL COMMENT '创建者（上传用户名，自动填充）',
    update_by     VARCHAR(100)  NULL COMMENT '更新者（自动填充）',
    created_at    DATETIME      COMMENT '创建时间 / 上传时间',
    updated_at    DATETIME      COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_created_at (created_at),
    INDEX idx_create_by (create_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件上传记录表';
