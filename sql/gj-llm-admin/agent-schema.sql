-- ============================================================
-- 模块：gj-llm-admin
-- 表名：vector_model（向量模型库配置表）
-- 用途：记录每个向量模型库的类型、集合名、描述等信息
-- ============================================================
CREATE TABLE IF NOT EXISTS vector_model (
    id              BIGINT        AUTO_INCREMENT COMMENT '主键（自增 ID）',
    type_code       VARCHAR(50)   NOT NULL COMMENT '类型编码（唯一，如 medical、story）',
    type_name       VARCHAR(100)  NOT NULL COMMENT '类型名称（如 医疗知识库）',
    collection_name VARCHAR(100)  NOT NULL COMMENT 'Milvus 集合名称（如 collection_medical）',
    description     VARCHAR(500)  NULL COMMENT '描述信息',
    status          TINYINT       DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_type_code (type_code),
    UNIQUE KEY uk_collection_name (collection_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='向量模型库配置表';
