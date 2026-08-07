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
    rerank_score_threshold DECIMAL(3,2) DEFAULT 0.30 COMMENT 'rerank 精排采纳阈值（默认0.3，评测后可采纳推荐值）',
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
    parent_id        VARCHAR(100)  NULL COMMENT '所属父块 ID（父子召回用，同父块子块共享）',
    chunk_index      INT           NULL COMMENT '子块在父块中的序号（调试用）',
    content          TEXT          NULL COMMENT '文本内容（可选，用于调试）',
    meta_data        JSON          NULL COMMENT 'JSON 格式存储额外元数据',
    created_at       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_dataset_file_id (dataset_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='切片元数据表';

-- ============================================================
-- 存量库迁移：为已存在的 document_segment 表补充父子召回字段
-- （新建库由上面的建表语句直接包含；存量库执行下方语句）
-- ============================================================
-- ALTER TABLE document_segment ADD COLUMN parent_id VARCHAR(100) NULL COMMENT '所属父块 ID' AFTER segment_id;
-- ALTER TABLE document_segment ADD COLUMN chunk_index INT NULL COMMENT '子块在父块中的序号' AFTER parent_id;

-- ============================================================
-- 存量库迁移：为已存在的 dataset 表补充 rerank 阈值字段
-- （新建库由上面的建表语句直接包含；存量库执行下方语句）
-- ============================================================
-- ALTER TABLE dataset ADD COLUMN rerank_score_threshold DECIMAL(3,2) DEFAULT 0.30 COMMENT 'rerank 精排采纳阈值';

-- 检索评测用例表（按知识库持久化 评测 query + 期望命中依据，供离线评测 Recall@K / MRR）
CREATE TABLE IF NOT EXISTS dataset_retrieval_eval_query (
    id               BIGINT        COMMENT '主键（雪花算法 ID）',
    dataset_id       BIGINT        NOT NULL COMMENT '关联的知识库 ID',
    query            VARCHAR(500)  NOT NULL COMMENT '评测查询（原始，不走改写，直接测索引+reranker 质量）',
    expected_source  VARCHAR(200)  NULL COMMENT '期望命中来源文件名（与 source 元数据做包含匹配，可只填部分）',
    expected_snippet VARCHAR(1000) NULL COMMENT '期望命中文本片段（对子块/父块原文做逐字子串匹配，须原文摘录不可改写）',
    create_by        VARCHAR(50)   DEFAULT NULL COMMENT '创建者',
    update_by        VARCHAR(50)   DEFAULT NULL COMMENT '更新者',
    created_at       DATETIME      COMMENT '创建时间',
    updated_at       DATETIME      COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_dataset_id (dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库检索评测用例表';
