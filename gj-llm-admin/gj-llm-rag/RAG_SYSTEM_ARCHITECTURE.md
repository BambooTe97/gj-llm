# RAG 系统架构文档

## 目录

1. [系统概述](#1-系统概述)
2. [整体架构](#2-整体架构)
3. [模块分层结构](#3-模块分层结构)
4. [文档摄取管道](#4-文档摄取管道)
5. [文本分块策略](#5-文本分块策略)
6. [向量嵌入与索引](#6-向量嵌入与索引)
7. [检索管道](#7-检索管道)
8. [Re-Ranker 精排](#8-re-ranker-精排)
9. [查询改写与 HyDE](#9-查询改写与-hyde)
10. [LLM 对话集成](#10-llm-对话集成)
11. [术语表](#11-术语表)
12. [典型场景与案例](#12-典型场景与案例)
13. [已知局限与改进方向](#13-已知局限与改进方向)

---

## 1. 系统概述

本系统是一个基于 Java/Spring Boot 的 **RAG（Retrieval-Augmented Generation，检索增强生成）** 知识库问答系统。核心能力是将用户上传的文档（PDF、文本等）自动处理为可检索的知识单元，在用户提问时从知识库中检索最相关的片段，结合 LLM 生成准确、有据可查的回答。

### 1.1 核心能力

| 能力 | 说明 |
|------|------|
| 文档自动向量化 | 上传文件 → 自动解析 → 智能分块 → 向量嵌入 → 入库 |
| 混合检索 | BM25 关键词 + KNN 语义，双路召回融合 |
| 精排重打分 | Cross-Encoder 逐对计算 query-document 相关性 |
| 查询改写 | 口语化自动转书面语 + HyDE 生成假设答案 |
| 流式对话 | SSE 流式输出，支持 thinking/references/content 事件 |
| 多知识库管理 | 每个知识库独立 ES 索引 + Milvus 集合，物理隔离 |

### 1.2 技术栈

| 组件 | 选型 | 用途 |
|------|------|------|
| 框架 | Spring Boot 4.1 + Spring AI 2.0 | 应用框架 |
| LLM | gemma2:2b (Ollama) | 对话生成、查询改写、HyDE |
| Embedding | BGE-M3 (Ollama) | 文本转向量（1024维） |
| 检索引擎 | Elasticsearch 9.x | BM25 全文检索（dense 走 Milvus 时 ES 不存向量，省内存） |
| 向量库 | Milvus | 稠密向量检索（默认 dense 源，专业向量库；可配切换为 ES KNN） |
| Re-Ranker | BGE-Reranker (TEI) | Cross-Encoder 精排 |
| 分词器 | IK Analyzer | 中文分词 |
| 数据库 | MySQL + MyBatis-Plus | 元数据持久化 |
| 文件存储 | 本地磁盘 | 上传文件物理存储 |

---

## 2. 整体架构

### 2.1 系统拓扑

```
┌─────────────────────────────────────────────────────────┐
│                    用户浏览器 (SSE)                       │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│              gj-llm-start (Spring Boot)                  │
│  ┌──────────────────────────────────────────────────┐   │
│  │              gj-llm-chat (对话编排模块)            │   │
│  │  Agent/AgentRouter/AgentRegistry: 智能体策略编排   │   │
│  │  ChatServiceImpl: 瘦编排(校验/路由/持久化)         │   │
│  └──────────────────────┬───────────────────────────┘   │
│                         │ RetrievalService 门面          │
│  ┌──────────────────────▼───────────────────────────┐   │
│  │              gj-llm-rag (RAG 核心)                 │   │
│  │  RetrievalService: 检索门面(改写/粗排/精排/护栏)   │   │
│  │  HybridSearcher: BM25+dense+RRF 融合              │   │
│  │  DenseRetriever: dense 策略(Milvus/EsKnn 二选一)   │   │
│  │  DatasetFileServiceImpl: 文件管道(写 ES+Milvus)    │   │
│  │  QueryRewriter: 查询改写 + HyDE(ChatModel)         │   │
│  │  ParentChildSplitter: 父子切分              │   │
│  │  DynamicVectorStoreManager: Milvus 管理            │   │
│  └──────┬──────────────────────────────┬─────────────┘   │
│         │                              │                  │
│  ┌──────▼──────┐  ┌──────────────┐  ┌──▼─────────────┐  │
│  │  gj-es      │  │ gj-reranker  │  │  gj-file       │  │
│  │  ES 原子检索 │  │ 精排重打分   │  │  文件存储管理   │  │
│  │ bm25/knnDocs│  │              │  │                │  │
│  └──────┬──────┘  └──────┬───────┘  └────────────────┘  │
└─────────┼────────────────┼──────────────────────────────┘
          │                │
┌─────────▼────────────────▼──────────────────────────────┐
│                 中间件服务层 (192.168.40.130)             │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐   │
│  │ ES 9.4.2 │  │  Milvus  │  │ TEI (BGE-Reranker)   │   │
│  │ :9200    │  │  :19530  │  │ :3000                │   │
│  └──────────┘  └──────────┘  └──────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Ollama :11434                        │   │
│  │  gemma2:2b (对话) / BGE-M3 (嵌入)               │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 2.2 数据流全景

```
┌─────────────────── 写入路径（文档上传） ───────────────────┐
                                                             │
  PDF/文本上传                                                │
    → FileStorageService: 保存到磁盘                          │
    → DatasetFileEntity: 创建数据库关联 (状态=PENDING)        │
    → @Async: 异步处理                                        │
        → PdfContentReader/TextContentReader: 内容提取        │
        → ParentChildSplitter: 父子切分 + ChunkContextEnricher: 上下文注入            │
        → EmbeddingModel(BGE-M3): 批量向量嵌入 (每批20条)     │
        → EsSearchService.indexDocuments 写 ES(文本始终;embedding 仅 provider=es) + [provider=milvus] VectorStore.add 写 Milvus 向量     │
        → DocumentSegmentEntity: 切片元数据持久化             │
    → 状态更新: COMPLETED                                     │
                                                             │
┌─────────────────── 读取路径（用户提问） ───────────────────┐
                                                             │
  用户口语化提问                                              │
    → QueryRewriter.rewrite(): 书面语改写 + HyDE 假设答案     │
    → HybridSearcher.search(): 多路混合检索            │
        ├─ BM25: ik_max_word 关键词匹配                      │
        ├─ Dense(Milvus/ES): HNSW 图搜索 + 余弦相似度                     │
        └─ RRF: 加权倒数排名融合                              │
    → RerankerService.rerank(): 精排 + 父子去重 + 阈值过滤           │
    → LLM (gemma2:2b): 流式生成回答                        │
    → SSE: thinking → no_result? → references → content → done            │
```

---

## 3. 模块分层结构

```
gj-llm/
├── gj-core/                         # 核心基础设施层
│   ├── gj-common/                   # 通用工具 (JacksonUtils等)
│   ├── gj-es/                       # ES 检索引擎模块
│   │   ├── config/                  # EsProperties, ElasticsearchConfig
│   │   ├── model/                   # EsDocumentSource
│   │   └── service/                 # EsSearchService
│   ├── gj-reranker/                 # Re-Ranker 精排模块
│   │   ├── config/                  # RerankerProperties
│   │   └── service/                 # RerankerService
│   ├── gj-file/                     # 文件存储模块
│   ├── gj-security/                 # 安全认证模块
│   └── gj-mybatis/                  # 持久层配置
│
├── gj-llm-admin/                    # 业务模块层
│   ├── gj-llm-rag/                  # RAG 核心模块
│   │   ├── entity/                  # DatasetEntity, DatasetFileEntity, DocumentSegmentEntity
│   │   ├── model/                   # DTO: DatasetCreateRequest, TestRankedResult 等
│   │   ├── service/                 # DatasetService, DatasetFileService, QueryRewriter, RetrievalService(门面), HybridSearcher(融合), DenseRetriever(Milvus/EsKnn 策略)
│   │   ├── vector/
│   │   │   ├── splitter/            # RecursiveCharacterTextSplitter(切分核心), ParentChildSplitter(父子切分),
│   │   │   │                        # Chunk, ChunkSeparators(类型分发), ChunkContextEnricher(上下文注入)
│   │   │   ├── reader/              # PdfContentReader, TextContentReader
│   │   │   └── DynamicVectorStoreManager
│   │   ├── eval/                    # RetrievalEvaluator (离线检索评测)
│   │   ├── config/                  # RagProperties, MilvusConfig
│   │   ├── listener/                # DatasetFileEventListener (异步事件)
│   │   └── controller/              # DatasetController (REST API + /eval 评测)
│   │
│   └── gj-llm-chat/                 # 对话模块
│       ├── agent/                  # ChatAgent 策略 + AgentRouter + AgentRegistry + RagQaAgent/ChitchatAgent/RemoteHttpAgent
│       └── service/impl/            # ChatServiceImpl 瘦编排(校验/路由/持久化)
│
└── gj-llm-start/                    # 启动模块 (Spring Boot 入口)
```

### 3.1 分层职责

| 层次 | 模块 | 职责 |
|------|------|------|
| 启动层 | gj-llm-start | Spring Boot 入口，聚合所有模块 |
| 业务层 | gj-llm-chat | 智能体编排(Agent/Router/Registry)、SSE 流控、持久化 |
| 业务层 | gj-llm-rag | 知识库管理、文档管道、查询改写、分块、检索编排(HybridSearcher)、dense 策略 |
| 基础设施层 | gj-es | ES 索引管理、BM25/KNN 原子检索(融合在 rag 的 HybridSearcher) |
| 基础设施层 | gj-reranker | Cross-Encoder 精排、TEI API 调用 |
| 基础设施层 | gj-file | 文件上传/下载/删除 |
| 基础设施层 | gj-common | JacksonUtils、日期工具等通用能力 |

---

## 4. 文档摄取管道

### 4.1 完整流程

```
用户上传文件
    │
    ▼
DatasetFileServiceImpl.upload()
    │
    ├─ FileStorageService.upload()           → 文件保存到磁盘 + file_record 表
    ├─ DatasetFileEntity 入库 (status=PENDING) → dataset_file 表
    ├─ 更新 dataset.docCount
    └─ 发布 DatasetFileUploadedEvent
            │
            ▼ (@Async + @TransactionalEventListener)
DatasetFileEventListener.handleUploaded()
    │
    └─ DatasetFileServiceImpl.processDatasetFile()
            │
            ├─ status → PROCESSING (5%)
            ├─ FileContentReader.read()        → 按扩展名选择读取器
            ├─ status → 文本切分中 (30%)
            ├─ ParentChildSplitter              → 父子两级切分（ChunkSeparators 按类型选分隔符）
            ├─ ChunkContextEnricher            → 确定性上下文前缀注入（零 LLM）
            ├─ ContextualRetrievalEnricher     → 可选 LLM 上下文（默认关）
            ├─ status → 向量嵌入 (50%)
            ├─ EmbeddingModel.embed(batch)     → BGE-M3 批量向量化
            ├─ EsSearchService.indexDocuments() → ES 写入(文本始终;embedding 仅 provider=es); [provider=milvus] VectorStore.add 写 Milvus 向量+content+metadata
            ├─ status → 保存元数据 (90%)
            ├─ DocumentSegmentEntity 批量入库   → document_segment 表（含 parent_id/chunk_index）
            └─ status → COMPLETED (100%)
```

### 4.2 文件读取器

| 读取器 | 支持格式 | 实现 |
|--------|---------|------|
| PdfContentReader | `.pdf` | Spring AI ParagraphPdfDocumentReader，回退 PagePdfDocumentReader |
| TextContentReader | `.txt, .md, .json, .xml, .csv, .html, .java, .py, .sql` 等 | 纯文本读取，整个文件作为一个 Document |

### 4.3 已支持 / 未支持的格式

| 格式 | 状态 | 说明 |
|------|------|------|
| PDF | ✅ 已支持 | 段落级解析 |
| TXT, MD, JSON, CSV, HTML 等 | ✅ 已支持 | 纯文本读取 |
| DOC, DOCX | ❌ 未支持 | 需要 Apache POI |
| XLS, XLSX | ❌ 未支持 | 需要 Apache POI |
| PPT, PPTX | ❌ 未支持 | 需要 Apache POI |
| 图片 (PNG, JPG) | ❌ 未支持 | 需要 OCR 或多模态模型 |

---

## 5. 文本分块策略

采用**父子两级切分 + 确定性上下文注入**，兼顾检索精度与上下文完整性。核心组件均在 `com.gj.llm.rag.vector.splitter` 包下。

### 5.1 RecursiveCharacterTextSplitter（切分核心）

递归降级分隔符切分，模仿 LangChain 设计。核心方法 `splitIntoSegments` 按分隔符层级从粗到细切分，返回**边界感知、无 overlap、每段 ≤ maxSize** 的片段：

```
散文分隔符层级（从粗到细）：
    "\n\n" -> "\n" -> "。！？；，" -> ".!?;," -> ":" -> " " -> ""(字符兜底)
```

三项关键修正（修复旧实现的缺陷）：
1. **句子级 overlap**：overlap 取上一段的尾部完整句子拼到下一段开头（边界感知），不再把 chunks 拼回原文做盲字符滑窗。旧实现的 `applyOverlap` 会在 overlap>0 时让递归边界切分整体失效（默认 overlap=150 即触发）。
2. **短块合并**：长度 < minChunkLength 的块并入相邻块，不再静默丢弃（避免数据丢失）。
3. **codepoint 安全**：字符兜底用 `offsetByCodePoints`，不切断 emoji/生僻字的 UTF-16 代理对。

### 5.2 内容类型分发（ChunkSeparators）

按文件扩展名选择分隔符，避免在代码/结构化数据上套用散文标点：

| 类型 | 扩展名 | 分隔符层级 |
|------|--------|-----------|
| 散文 PROSE | pdf/md/doc/txt 等 | 中英文句末标点 |
| 代码 CODE | java/py/js/ts/sql/sh 等 | `\n\n` -> `\n` -> `}` -> `{` -> `;` -> ` ` |
| JSON | json | `\n\n` -> `\n` -> `}` -> `{` -> `,` -> ` ` |
| CSV | csv | `\n` -> `\r\n`（按行，保持记录完整） |

### 5.3 父子切分（ParentChildSplitter，small-to-big）

两级粒度切分，是父子召回的基础：

```
reader-Document
    └─ 父块切分（parentSize，无 overlap，靠 \n\n 段落边界对齐）
         ├─ 父块1 ─┬─ 子块1（childSize + 句子级 overlap）
         │         ├─ 子块2
         │         └─ 子块3
         └─ 父块2 ─┬─ 子块4
                   └─ 子块5
```

- **子块**（childSize = dataset.chunkSize）：embedding + BM25 的检索单元，精确匹配
- **父块**（parentSize = childSize × `parent-size-multiplier`，默认 3）：命中子块后返回 LLM 的完整上下文
- 同一父块的子块共享 `parentId`，检索侧据此去重

### 5.4 确定性上下文注入（ChunkContextEnricher）

每个子块文本前拼接文档标识前缀（零 LLM 成本）：

```
[文档: 配置手册.pdf > 数据库连接配置]
<原始子块文本>
```

前缀进 ES `content` 字段后，BM25 与 embedding 同时吃到标题/来源关键词，chunk 不再是无来源的孤儿片段。`title` 取自 Markdown reader 的 heading metadata；PDF/Tika/Text 无 title 时仅用 source。开关 `gj.llm.rag.context-prefix-enabled`（默认 true）。

### 5.5（可选）LLM Contextual Retrieval

`ContextualRetrievalEnricher` 在入库时调 gemma2:2b 给每个父块生成一句上下文说明，拼到子块前缀（在 5.4 确定性前缀之上）。**默认关闭**（`contextual-retrieval-enabled=false`），因 2b 模型质量不确定，须用 §7.7 评测器证明有正收益再开。

### 5.6 关键参数

| 参数 | 来源 | 默认 | 说明 |
|------|------|------|------|
| chunkSize | DatasetEntity | 500（create 缺省 600） | 子块目标字符数 |
| chunkOverlap | DatasetEntity | 150 | 子块 overlap（句子级） |
| minChunkLength | 硬编码 | 20 | 短块合并阈值 |
| parent-size-multiplier | RagProperties | 3 | parentSize = chunkSize × 此值 |
| context-prefix-enabled | RagProperties | true | 确定性上下文前缀开关 |
| contextual-retrieval-enabled | RagProperties | false | LLM 上下文（可选） |

---

## 6. 向量嵌入与索引

### 6.1 嵌入模型

使用 **BGE-M3**（BAAI General Embedding M3）通过 Ollama 部署：

| 属性 | 值 |
|------|-----|
| 模型 | bge-m3:latest |
| 输出维度 | 1024 维浮点向量 |
| 最大输入长度 | 8192 tokens |
| 特点 | 多语言、多粒度，中文检索性能优秀 |

**重要设计决策**：查询和文档使用**相同的嵌入方式**（不加指令前缀），确保两者在同一向量空间中。这是经过 A/B 测试验证的选择——Ollama 不支持 HuggingFace 的 prompt 指令机制，加前缀反而会偏移向量空间。

### 6.2 ES 索引结构

每个知识库一个独立索引，命名规则：`rag_{collectionName}`

```json
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "ik_max_word_analyzer": { "type": "ik_max_word" }
      }
    }
  },
  "mappings": {
    "properties": {
      "content": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "embedding": {
        "type": "dense_vector",
        "dims": 1024,
        "index": true,
        "similarity": "cosine",
        "index_options": {
          "type": "hnsw",
          "m": 32,
          "ef_construction": 200
        }
      },
      "dataset_id":     { "type": "long" },
      "dataset_file_id": { "type": "long" },
      "file_id":        { "type": "long" },
      "source":         { "type": "keyword" },
      "parent_id":      { "type": "keyword" },
      "parent_content": { "type": "text", "index": false },
      "chunk_index":    { "type": "integer" },
      "metadata":       { "type": "object", "enabled": false }
    }
  }
}
```

> **父子召回字段**：`parent_content`（命中子块后返回 LLM 的父块文本，`index:false` 仅存储不分析）、`parent_id`（同父块子块共享，检索侧去重）、`chunk_index`（子块序号）。旧索引无这些字段时检索侧自动回退用子块 content，向后兼容。
>
> **embedding 字段条件化**：`provider=milvus`（默认）时入库不写 `embedding`，ES 只存文本做 BM25（省内存，向量交 Milvus）；`provider=es` 时才写 `embedding` 做 ES KNN。mapping 始终声明该字段，不写则空（无 HNSW 图、不占内存）。

### 6.3 Milvus 集合

每个知识库在 Milvus 中也有一个对应集合（命名：`collection_{type}`），使用 HNSW 索引（M=16, efConstruction=200）、COSINE 度量。默认配置下（provider=milvus）Milvus 是 dense 检索源——向量只存 Milvus，ES 只做 BM25（省 ES 内存）；可配 provider=es 切回 ES KNN（向量需存 ES）。

---

## 7. 检索管道

### 7.1 完整检索链路

```
用户查询 (原始口语)
    │
    ▼
QueryRewriter.rewrite()
    ├─ 查询变体1: 书面语改写 ("如何进行系统配置")
    ├─ 查询变体2: 另一角度 ("系统参数设置方法")
    ├─ 查询变体3: HyDE 假设答案 ("配置系统参数需要先进入设置页面...")
    └─ 查询变体0: 原始查询 (保底)
    │
    ▼ (每个变体独立检索)
HybridSearcher.search()
    │
    ├─ BM25 搜索 ──────────────────────┐
    │   match query on "content" 字段   │
    │   分词器: ik_smart (搜索时)        │
    │   返回: candidateK=40 条           │
    │                                   │
    ├─ KNN 向量搜索 ───────────────────┤
    │   HNSW 图搜索 on "embedding" 字段 │
    │   相似度: Cosine                  │
    │   num_candidates: k*10 = 400     │
    │   返回: candidateK=40 条           │
    │                                   │
    └─ RRF 融合 ───────────────────────┘
        加权倒数排名融合:
          sparseWeight=0.3, denseWeight=0.7, k=60
        返回: Top-8 (VARIANT_TOP_K)
    │
    ▼
多路合并去重 (按文本内容去重，保留高分)
    │
    ▼ (最多 30 条候选)
RerankerService.rerank()
    │
    ▼ (返回 Top-5 子块)
父子召回去重 (按 parent_id 合并同父块，保留高分)
    │
    ▼
质量护栏 (rerank 分数 < 阈值 过滤)
    │
    ├─ 有可信结果: 父块上下文构建 (受 context-budget-chars 约束)
    │                │
    │                ▼
    │            LLM 上下文 → 流式生成回答
    │
    └─ 无可信结果: no_result 事件 + 我不知道 prompt（不编造）
```

### 7.2 BM25 检索

**BM25**（Best Match 25）是一种基于**词频-逆文档频率**的概率检索模型。它衡量查询词在文档中出现的频率，同时惩罚常见词。

- 当前实现：对 `content` 字段做标准 `match` 查询
- 索引时用 `ik_max_word`（最大粒度切分，提高召回）
- 搜索时用 `ik_smart`（智能切分，提高精度）

### 7.3 KNN 向量检索

**KNN**（K-Nearest Neighbors）在向量空间中查找与查询向量最相似的 K 个文档向量。

- 使用 ES 的 `dense_vector` 类型 + HNSW 近似索引
- 相似度度量：**余弦相似度**
- 查询前将文本通过 BGE-M3 转为 1024 维向量

### 7.4 RRF 融合

**RRF**（Reciprocal Rank Fusion，倒数排名融合）是一种将多个排序列表合并为一个的算法。

```
RRF_score(doc) = Σ w_i / (k + rank_i(doc))

其中:
  w_sparse = 0.3  (BM25 权重)
  w_dense  = 0.7  (KNN 权重)
  k = 60          (平滑参数，防止单条高分结果主导)
```

**为什么用 RRF？**
- 不依赖原始分数的量纲（BM25 分数和余弦相似度不可比）
- 只关心"相对排名"，天然适配异构排序列表
- k=60 是标准推荐值，确保排名靠后的结果也有一定贡献

**为什么 dense 权重 0.7 高于 sparse 权重 0.3？**
- 语义检索（向量）通常比关键词检索（BM25）更能理解用户意图
- 中文的 BM25 依赖 IK 分词器，专有名词切分可能不准
- 但 BM25 对精确关键词匹配仍然有价值，保留 30% 权重做互补

### 7.5 父子召回与质量护栏

精排后追加两步，让检索结果既完整又不编造：

1. **父子召回去重**：rerank 返回的 Top-5 是子块。按 `parent_id` 合并同父块子块（保留最高分），避免同一父块多个子块占用上下文名额。
2. **上下文构建**：用父块 `parent_content`（完整上下文）而非子块文本喂给 LLM；按 score 降序累加，受 `context-budget-chars`（默认 3500）约束，防止溢出 gemma2:2b 的 8k token 窗口。旧数据无 `parent_content` 时回退子块文本。
3. **质量护栏**：rerank 分数 < `rerank-score-threshold`（默认 0.3）的弱结果被过滤。若全部低于阈值或无检索结果，走"我不知道"分支：发 `no_result` SSE 事件 + 专用 prompt，不把弱上下文塞给 LLM 编造。

### 7.6 元数据过滤

`hybridSearch` 及其 filter 重载已随融合逻辑上移到 rag 的 `HybridSearcher` 而移除（ES 只保留 `bm25SearchDocs`/`knnSearchDocs` 原子检索）。元数据过滤暂未接入 HybridSearcher；如需"文档内检索"等场景，可在 HybridSearcher 增参支持。

### 7.7 离线检索评测

`RetrievalEvaluator`（`POST /api/v1/datasets/{id}/eval`）量化衡量切分/检索改动效果：对每条评测查询执行 HybridSearcher 检索 + rerank，判定期望是否进 Top-5，计算 **Recall@5** 与 **MRR**。配套 `eval-queries-sample.json` 示例。**所有调参（chunkSize、阈值、是否开 Contextual Retrieval）都应先跑评测对比指标再决定。**

---

## 8. Re-Ranker 精排

### 8.1 Bi-Encoder vs Cross-Encoder

这是理解 RAG 检索精度的关键概念：

```
Bi-Encoder（双塔模型，如 BGE-M3）:
  ┌─────────┐     ┌─────────┐
  │  Query  │     │Document │
  │  Encoder│     │ Encoder │
  └────┬────┘     └────┬────┘
       ▼               ▼
   ┌───────┐       ┌───────┐
   │Vec_Q  │       │Vec_D  │
   └───┬───┘       └───┬───┘
       └───────┬───────┘
               ▼
        cos(Vec_Q, Vec_D)

  特点：Query 和 Document 独立编码，速度快，精度有损
       因为 query 和 document 之间没有"交叉注意力"

Cross-Encoder（交叉编码器，如 BGE-Reranker）:
  ┌──────────────────────────────┐
  │  [CLS] Query [SEP] Document  │
  │         Transformer          │
  └──────────────┬───────────────┘
                 ▼
              Score

  特点：Query 和 Document 拼接后一起送入 Transformer
       Token 之间有完整的交叉注意力，精度显著更高
       但速度慢，只能用于精排少量候选
```

### 8.2 为什么需要 Re-Ranker

```
粗排 (Bi-Encoder):  1000 个文档 → Top 30    (快，精度 80% 左右)
精排 (Cross-Encoder):  30 个候选 → Top 5     (慢，但精度 95%+)
```

**这是 RAG 的标准两阶段架构**。单独用 Bi-Encoder 召回率不够，单独用 Cross-Encoder 太慢。两阶段结合是最佳实践。

### 8.3 BGE-Reranker-v2-m3

| 属性 | 完整版 (2.2G) | 简化版/量化版 (1G) |
|------|-------------|-------------------|
| 精度天花板 | 0.92-0.98 | 0.82-0.88 |
| 内存占用 | ~4GB | ~2GB |
| 推理速度 | 较慢 | 较快 |

当前部署的是简化版（受服务器内存限制），精排分数上限约为 0.85-0.88。

> **分数阈值护栏**：rerank 后过滤分数 < `gj.llm.rag.rerank-score-threshold`（默认 0.3）的结果。简化版 reranker 分数区间压缩（不相关查询约 0.5-0.6），0.3 为保守默认（基本不过滤、无回归风险），**建议用 §7.7 评测器标定后调高**。全部低于阈值时走"我不知道"分支（见 §7.5）。

---

## 9. 查询改写与 HyDE

### 9.1 为什么需要查询改写

用户提问和知识库文档之间存在**语义鸿沟**：

| 用户口语化提问 | 知识库文档内容 |
|-------------|-------------|
| "这玩意儿咋配" | "系统参数配置方法如下..." |
| "那个功能怎么搞" | "权限管理模块使用说明" |
| "能不能帮我看看" | "故障排查指南" |

直接拿口语化查询去检索，BM25 匹配不到关键词，KNN 向量也不在同一个语义子空间。

### 9.2 改写策略

**策略 1：书面语改写**

```
输入: "这玩意儿咋配"
输出: "如何进行系统参数配置"
      "系统配置操作步骤"
```

用 LLM 将口语化问题改写为正式检索查询，使用书面语和专业术语。

**策略 2：HyDE（Hypothetical Document Embeddings）**

```
输入: "这玩意儿咋配"

LLM 生成假设答案:
"配置系统参数需要先进入系统设置页面，在左侧导航栏中
 选择参数配置选项，找到对应的参数项后点击编辑按钮，
 修改参数值并保存即可生效。"

用这段"假设答案"去做向量检索 → embedding 天然接近真实文档的语义空间
```

HyDE 是当前 RAG 领域最有效的查询增强技术之一。假设答案虽然是 LLM "编"的，但它的**风格、术语、句式**与知识库文档高度一致，所以 embedding 匹配度远高于短查询。

### 9.3 多路合并

3-4 个查询变体分别检索，结果按文本内容去重（相同内容的 chunk 只保留最高分），合并后送 Re-Ranker。这样不同的查询角度能覆盖不同方面的答案。

---

## 10. LLM 对话集成

chat 模块采用**智能体编排架构**：`ChatServiceImpl` 是瘦编排器（校验会话 → 存用户消息 → `AgentRouter` 路由 → Agent 执行 → 存助手消息）；`Agent` 策略接口由 `RagQaAgent`（走 `RetrievalService` 检索 + RAG prompt）、`ChitchatAgent`（无检索）等实现，差异化用策略隔离；`AgentRegistry` 支持配置接入外部/市面智能体（`RemoteHttpAgent`）。检索不归 chat，由 rag 的 `RetrievalService` 门面提供。下面 Prompt/SSE 描述对应 `RagQaAgent` 的行为。

### 10.1 系统 Prompt

```
你是一个智能知识库助手。请根据【参考上下文】回答用户的问题。
如果上下文中没有答案或信息不足，请诚实地告诉用户你不知道，不要编造。
回答时请保持专业、准确、简洁。
```

### 10.2 用户 Prompt 结构

```
参考上下文:
{chunk1 文本}

{chunk2 文本}

{chunk3 文本}
...

用户问题:
{用户原始问题}
```

### 10.3 SSE 事件流

| 事件类型 | 触发时机 | 内容 |
|---------|---------|------|
| `thinking` | LLM 输出 thinking token 时 | gemma2:2b 的思考过程（若模型支持） |
| `no_result` | 检索无可靠结果时 | 提示"知识库中未找到相关内容"（见 §7.5 质量护栏） |
| `references` | 检索完成后 | 引用的文档片段（排名、内容摘要、分数、source、datasetFileId） |
| `content` | LLM 输出内容时 | 逐 token 流式回答 |
| `done` | 对话完成 | messageId, conversationId, title |

### 10.4 历史对话管理

- 最近 10 对（20 条）对话历史通过 `messages[]` 数组传递给 Ollama
- 每条消息标注 `user` / `assistant` 角色
- 历史消息独立于 RAG 上下文，互不干扰

---

## 11. 术语表

### 11.1 检索基础

| 术语 | 全称 | 解释 |
|------|------|------|
| **RAG** | Retrieval-Augmented Generation | 检索增强生成。在 LLM 生成回答前，先从知识库中检索相关文档作为参考上下文，避免 LLM "编造"答案 |
| **Chunk** | — | 文本块。长文档被切分为多个小块，每块独立向量化，作为检索的最小单元 |
| **Recall** | — | 召回率。所有相关文档中，被检索出来的比例。Recall@5 = 前5条结果中包含了多少真正相关的文档 |
| **Precision** | — | 精确率。检索出的文档中，真正相关的比例 |
| **Top-K** | — | 检索返回的前 K 条结果。本系统中 K=5 |

### 11.2 向量与相似度

| 术语 | 解释 |
|------|------|
| **Embedding** | 嵌入向量。将文本通过模型转换为固定维度的浮点数数组。语义相近的文本，向量在空间中距离相近 |
| **Cosine Similarity** | 余弦相似度。两个向量夹角的余弦值。范围 [-1, 1]，1 表示完全相同，0 表示无关，-1 表示完全相反。本系统使用余弦相似度作为向量匹配的度量 |
| **Dense Vector** | 稠密向量。每个维度都有值的向量。BGE-M3 输出 1024 维稠密向量 |
| **Bi-Encoder** | 双塔编码器。Query 和 Document 分别独立编码为向量，通过向量距离判断相关性。速度快但精度有损 |
| **Cross-Encoder** | 交叉编码器。将 Query 和 Document 拼接后一起输入 Transformer，通过交叉注意力计算相关性。精度高但速度慢 |

### 11.3 检索算法

| 术语 | 解释 |
|------|------|
| **BM25** | Best Match 25。基于词频和逆文档频率的概率检索模型。核心思想：查询词在文档中出现越频繁，文档越相关；但常见词（如"的""是"）权重低 |
| **KNN** | K-Nearest Neighbors。K 最近邻。在向量空间中找出与目标向量最相似的 K 个邻居向量 |
| **ANN** | Approximate Nearest Neighbor。近似最近邻。用 HNSW 等索引结构加速搜索，精度换速度，通常能达到 95-99% 的真实最近邻精度 |
| **RRF** | Reciprocal Rank Fusion。倒数排名融合。将多个排序列表合并为一个的算法。公式：`score = Σ w/(k+rank)`。不依赖原始分数的量纲，只关心排名 |
| **HyDE** | Hypothetical Document Embeddings。假设文档嵌入。让 LLM 先生成一个"假设答案"，用这个答案的 embedding 去检索。假设答案的文档风格和真实知识库内容对齐，匹配度远高于短查询 |

### 11.4 向量索引

| 术语 | 解释 |
|------|------|
| **HNSW** | Hierarchical Navigable Small World。分层可导航小世界图。一种高效的 ANN 索引算法。构建多层图结构，每层节点只连接最近的 M 个邻居。搜索时从顶层粗糙匹配逐步下钻到最底层精确匹配，复杂度 O(log N) |
| **M** | HNSW 图中每个节点的最大连接数。越大则索引精度越高，但内存占用和构建时间也越大。默认 16，本系统设为 32 |
| **ef_construction** | HNSW 索引构建时的候选队列大小。越大索引精度越高，但构建越慢。默认 100，本系统设为 200 |
| **num_candidates** | KNN 搜索时从 HNSW 图中探索的候选节点数。越大搜索精度越高，但速度越慢。本系统设为 k 的 10 倍 |

### 11.5 中文分词

| 术语 | 解释 |
|------|------|
| **IK Analyzer** | 一款开源中文分词器。将中文文本切分为有意义的词语，用于全文检索 |
| **ik_max_word** | IK 分词器的"最大粒度"模式。将文本尽可能多地切分为词语。例如："中华人民共和国" → "中华人民共和国 / 中华人民 / 中华 / 华人 / 人民 / 共和国 / 共和"。用于**索引时**，最大化召回 |
| **ik_smart** | IK 分词器的"智能"模式。做最粗粒度的切分。例如："中华人民共和国" → "中华人民共和国"。用于**搜索时**，提高精度 |

### 11.6 架构模式

| 术语 | 解释 |
|------|------|
| **粗排 → 精排** | 两阶段检索。第一阶段（粗排）用快速但精度有限的方法（Bi-Encoder + BM25）从海量文档中筛选候选；第二阶段（精排）用精确但慢的方法（Cross-Encoder）对候选重排序。折中了速度与精度 |
| **SSE** | Server-Sent Events。服务端推送事件。HTTP 长连接，服务端可以持续推送数据流，适合 LLM 逐 token 输出的场景 |
| **@Async** | Spring 的异步注解。文件向量化是耗时操作，通过异步事件解耦，上传请求立即返回，向量化在后台线程池执行 |
| **Bulk API** | ES 的批量写入接口。一次请求写入多条文档，显著提升索引效率。本系统每批 20 条 embed + bulk 写入 |

---

## 12. 典型场景与案例

### 场景 1：精确匹配查询

```
用户提问: "数据库连接池的最大连接数是多少？"

知识库中存在: "数据库连接池的最大连接数默认为 100，
             可以根据并发量调整..."

检索过程:
  ① 查询改写: "数据库连接池最大连接数配置" + HyDE 假设答案
  ② BM25: 命中 "连接池"、"最大连接数" → 高分匹配
  ③ KNN: 向量语义匹配 → 高分匹配
  ④ RRF 融合: BM25 #1 + KNN #1 → 排名榜首
  ⑤ Re-Ranker 精排: score 0.88

结果: ✅ 精确命中，回答准确
```

### 场景 2：口语化语义查询

```
用户提问: "这玩意儿咋连数据库啊？"

知识库中存在: "数据库连接配置方法：在 application.yml 中
             配置 spring.datasource.url、username、password..."

检索过程:
  ① 查询改写: "如何配置数据库连接" + "数据库连接配置方法"
     + HyDE: "要配置数据库连接，需要设置以下参数..."
  ② BM25: "咋连数据库" 没直接匹配关键词 → 低分
  ③ KNN: 向量语义相近 → 中等分
  ④ 多路合并: HyDE 假设答案的检索结果命中目标
  ⑤ Re-Ranker: 原始口语 vs 文档逐字匹配 → score 0.85

结果: ✅ 语义理解正确，回答准确
       但分数低于精确匹配（0.85 vs 0.88），符合预期
```

### 场景 3：不相关查询

```
用户提问: "今天天气怎么样？"

知识库内容: 全部为技术文档

检索过程:
  ① 查询改写: "今日天气情况查询" + HyDE 假设答案
  ② BM25: 没有匹配关键词
  ③ KNN: 向量空间中距离所有文档都远 → 低余弦相似度
  ④ Re-Ranker: 不相关 → score 0.5-0.6

结果: ✅ 分数显著低于相关查询，可区分相关/不相关
      但系统仍会返回"最接近"的文档（非完美方案）
```

### 场景 4：多文档综合查询

```
用户提问: "权限管理和角色配置有什么关系？"

知识库中有:
  文档A: "权限管理模块..."
  文档B: "角色配置指南..."

检索过程:
  ① 查询改写: "权限管理和角色配置的关系"
     + "权限管理模块与角色配置的关联"
     + HyDE: "权限管理和角色配置是密切相关的..."
  ② 多路检索: 不同变体分别命中文档A和文档B
  ③ 去重合并: 两份文档的 chunk 都进入候选池
  ④ Re-Ranker: 筛选出最相关的前5条

结果: ✅ 跨文档检索成功，LLM 综合多文档信息回答
```

---

## 13. 已知局限与改进方向

### 13.1 已实现的关键优化（本轮）

| 优化项 | 解决的问题 | 实现位置 |
|--------|-----------|---------|
| 切分器句子级 overlap + 短块合并 + codepoint 安全 | overlap 破坏边界、短块丢失、emoji 断裂 | RecursiveCharacterTextSplitter |
| 父子两级切分（small-to-big） | 检索精度与上下文完整性兼得 | ParentChildSplitter |
| 内容类型分发分隔符 | 代码/JSON/CSV 不再套用散文标点 | ChunkSeparators |
| 确定性上下文前缀注入 | chunk 不再是无来源孤儿，标题/来源进 embedding+BM25 | ChunkContextEnricher |
| ES 父子字段 + 检索侧父块回填 + 去重 | 命中子块返回父块完整上下文 | RetrievalServiceImpl |
| rerank 分数阈值 + "我不知道"硬拦截 | 弱结果不编造，不相关查询诚实返回空 | RetrievalServiceImpl / RagQaAgent |
| 上下文字符预算护栏 | 防止父块拼接溢出 gemma2:2b 8k 窗口 | RetrievalServiceImpl |
| 离线检索评测（Recall@5 / MRR） | 调参不盲 | RetrievalEvaluator |
| 可选 LLM Contextual Retrieval（默认关） | 进一步提升 embedding 命中（需评测验证） | ContextualRetrievalEnricher |

### 13.2 仍存在的局限

#### 模型层面

| 局限 | 影响 | 改进方向 |
|------|------|---------|
| BGE-M3 为 Ollama 量化版 | 向量质量低于 fp16 原版 | 换非量化版或更强的 Embedding 模型 |
| Re-Ranker 为 1G 简化版 | 精排分数上限 0.85-0.88 | 换完整 2.2G 版可提升到 0.92+ |
| gemma2:2b 能力有限 | 复杂推理/查询改写质量弱于大模型 | 资源允许时换更强模型 |
| 无领域微调 | 专有名词、行业术语的语义理解不精准 | 收集标注数据做微调 |

#### 检索层面

| 局限 | 影响 | 改进方向 |
|------|------|---------|
| BM25 无自定义词典 | 领域专有名词被 IK 错误切分 | 配置 IK 自定义词典 |
| BM25 无查询扩展 | 同义词不能互相匹配 | 同义词扩展 |
| rerank 阈值未标定 | 0.3 默认偏保守，可能未过滤不相关 | 用 §7.7 评测器标定合适阈值 |
| Top-K 固定为 5 | 简单/复杂查询用同样返回数 | 动态 Top-K |

#### 文档处理层面

| 局限 | 影响 | 改进方向 |
|------|------|---------|
| 不支持 DOCX/XLSX/PPT | 常见办公文档格式无法处理 | 集成 Apache POI 或 Tika |
| PDF 表格/列表解析差 | 结构化内容丢失 | 换更强大的 PDF 解析器（MinerU/Docling） |
| 无图片/OCR 支持 | 图片中的文字无法检索 | 集成 OCR 或多模态模型 |
| 结构化解析未做 | 标题层级路径仅 Markdown 有 title | 引入 MinerU/Docling 版面解析 |

#### 工程层面

| 局限 | 影响 | 改进方向 |
|------|------|---------|
| 无用户反馈闭环 | 无法知道哪些回答被用户认可 | 点赞/踩 + 日志分析 |
| 事件驱动无持久化 | 服务重启丢事件，文件卡在 PENDING | 引入消息队列 |
| 无缓存机制 | 相似问题重复检索全链路 | 查询结果缓存 |

### 13.3 改进优先级建议（剩余）

| 优先级 | 改进项 | 预期收益 | 实现难度 |
|--------|-------|---------|---------|
| P0 | IK 自定义词典 | 专有名词匹配提升 3-5% | 低（配置文件） |
| P0 | 用评测器标定 rerank 阈值 | 不相关查询正确返回空 | 低 |
| P1 | 升级 Re-Ranker 到完整版 | 精排分数突破 0.90 | 低（替换模型文件） |
| P1 | 动态 Top-K | 简单/复杂查询差异化返回 | 低 |
| P2 | 支持 DOCX/XLSX 格式 | 扩大可用文档类型 | 中 |
| P2 | MinerU/Docling 结构化解析 | 标题层级/表格保真 | 中（需新工具） |
| P2 | 查询缓存 | 减少重复检索 | 中 |
| P3 | Embedding 模型微调 | 领域适配，天花板整体上移 | 高（需标注数据） |
| P3 | 图片/OCR 支持 | 多模态检索 | 高 |

---

## 附录：关键配置文件

### application-ai.yml

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: gemma2:2b
      embedding:
        model: bge-m3:latest
    vectorstore:
      milvus:
        initialize-schema: false
        database-name: default
        embedding-dimension: 1024
        index-type: hnsw
        metric-type: cosine
        client:
          host: 192.168.40.130
          port: 19530

gj:
  llm:
    es:
      host: 192.168.40.130
      port: 9200
      index-prefix: rag_
      embedding-dimension: 1024
      shards: 1
      replicas: 0
    reranker:
      enabled: true
      host: 192.168.40.130
      port: 3000
      timeout: 30000
    rag:
      parent-size-multiplier: 3
      context-prefix-enabled: true
      rerank-score-threshold: 0.3
      context-budget-chars: 3500
      contextual-retrieval-enabled: false
      rewrite-model: gemma2:2b        # rag 自配的查询改写模型
      dense:
        provider: milvus              # milvus(向量走 Milvus,ES 只做 BM25,省内存) | es(ES 做 KNN)
```
