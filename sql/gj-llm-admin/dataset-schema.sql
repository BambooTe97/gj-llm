-- ============================================================
-- 模块：gj-llm-admin
-- 表名：dataset / dataset_file / document_segment
-- 用途：知识库管理 —— 知识库配置、知识库-文件关联、切片元数据
-- ============================================================

-- 知识库表
CREATE TABLE IF NOT EXISTS dataset (
    id               BIGINT        COMMENT '主键（雪花算法 ID）',
    name             VARCHAR(100)  NOT NULL COMMENT '知识库名称',
    description      VARCHAR(500)  NULL COMMENT '描述信息',
    embedding_model  VARCHAR(100)  NOT NULL COMMENT 'Embedding 模型标识（如 BGE-Large-ZH）',
    vector_store_type VARCHAR(50)  NOT NULL COMMENT '向量库类型（如 Milvus、PostgreSQL）',
    collection_name  VARCHAR(100)  NOT NULL COMMENT '向量库中的集合名称',
    chunk_size       INT           DEFAULT 800 COMMENT '切片大小（字符数）',
    chunk_overlap    INT           DEFAULT 100 COMMENT '切片重叠（字符数）',
    status           VARCHAR(20)   DEFAULT 'READY' COMMENT '状态：READY=就绪, INDEXING=索引中, ERROR=异常',
    doc_count        INT           DEFAULT 0 COMMENT '文档数量',
    segment_count    INT           DEFAULT 0 COMMENT '向量数量（切片总数）',
    created_at       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库配置表';

-- 知识库-文件关联中间表
-- 关联 file_record 表，存放知识库特有的处理元数据
CREATE TABLE IF NOT EXISTS dataset_file (
    id               BIGINT        COMMENT '主键（雪花算法 ID）',
    dataset_id       BIGINT        NOT NULL COMMENT '关联的知识库 ID',
    file_id          BIGINT        NOT NULL COMMENT '关联的文件记录 ID（file_record.id）',
    status           VARCHAR(20)   DEFAULT 'PENDING' COMMENT '处理状态：PENDING=排队中, PROCESSING=向量化中, COMPLETED=完成, FAILED=失败',
    error_message    VARCHAR(1000) NULL COMMENT '失败原因',
    segment_count    INT           DEFAULT 0 COMMENT '生成的切片数量',
    progress_percent INT           DEFAULT 0 COMMENT '向量化进度百分比 0-100',
    current_step     VARCHAR(50)   NULL COMMENT '当前处理阶段描述',
    created_at       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库-文件关联表';

-- 切片元数据表（用于调试和删除时定位向量数据）
CREATE TABLE IF NOT EXISTS document_segment (
    id               BIGINT        COMMENT '主键（雪花算法 ID）',
    dataset_file_id  BIGINT        NOT NULL COMMENT '关联的 dataset_file ID',
    segment_id       VARCHAR(100)  NOT NULL COMMENT '对应向量数据库中的 ID',
    content          TEXT          NULL COMMENT '文本内容（可选，用于调试）',
    meta_data        JSON          NULL COMMENT 'JSON 格式存储额外元数据',
    created_at       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_dataset_file_id (dataset_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='切片元数据表';
