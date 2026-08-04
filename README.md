<p align="center">
  <h1 align="center">🤖 GJ-LLM</h1>
  <p align="center">
    <strong>企业级 LLM 知识库平台 -- 基于 RAG 的智能知识管理与对话系统</strong>
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk" />
    <img src="https://img.shields.io/badge/Spring_Boot-4.1-brightgreen?style=flat-square&logo=springboot" />
    <img src="https://img.shields.io/badge/Spring_AI-2.0-green?style=flat-square&logo=spring" />
    <img src="https://img.shields.io/badge/Vue-3.5-blue?style=flat-square&logo=vuedotjs" />
    <img src="https://img.shields.io/badge/Elasticsearch-9.x-yellow?style=flat-square&logo=elasticsearch" />
    <img src="https://img.shields.io/badge/Milvus-2.3+-00BEBE?style=flat-square&logo=milvus" />
    <img src="https://img.shields.io/badge/Redis-7.4-red?style=flat-square&logo=redis" />
    <img src="https://img.shields.io/badge/license-MIT-blue?style=flat-square" />
  </p>
</p>

---

## 📖 项目简介

**GJ-LLM** 是一个面向企业的智能知识库平台，深度融合 **大语言模型（LLM）** 与 **检索增强生成（RAG）** 技术。用户上传文档后，系统自动解析、分块、向量化存入知识库，提问时经混合检索 + 精排拿到最相关片段，结合 LLM 给出精准、可溯源的回答--让 AI 真正「读懂」你的私有知识。

## ✨ 核心功能

- 🗣️ **和你的文档对话** - 上传 PDF / Word / Markdown / Excel 等文档，基于文档内容智能问答
- 💬 **多轮 AI 对话** - SSE 流式输出，支持思考过程、引用溯源展示、对话中断
- 🎯 **精准检索** - 混合检索（BM25 + KNN + RRF）+ Cross-Encoder 精排 + 查询改写 / HyDE，口语化提问也能命中
- 📚 **知识库管理** - 多知识库独立隔离（ES 索引 + Milvus 集合），文档异步向量化
- 🔐 **企业级权限** - 完整 RBAC 动态菜单 + 按钮级细粒度权限，JWT 双令牌 + 登出黑名单

## 🖼️ 项目截图

<!-- 截图放置建议：在仓库根目录新建 docs/screenshots/，存入对应界面截图后取消注释替换占位 -->
<!-- ![对话界面](docs/screenshots/chat.png) -->
<!-- ![知识库管理](docs/screenshots/dataset.png) -->
<!-- ![系统管理](docs/screenshots/system.png) -->

> 📌 截图整理中，将陆续补充：对话界面（思考过程 + 引用溯源）、知识库与文档管理、系统管理（用户 / 角色 / 菜单）。

---

## 🚀 快速开始

### 环境要求

- **Java** 25+ · **Maven** 3.9+ · **Node.js** 18+ · **pnpm**
- **MySQL** 8.0+ · **Milvus** 2.3+ · **Redis** 7.4+ · **Elasticsearch** 9.x · **Ollama**

### 启动步骤

```bash
# 1. 克隆项目
git clone https://github.com/BambooTe97/gj-llm.git
cd gj-llm

# 2. 初始化数据库（执行 sql/ 目录下的脚本）
mysql -u root -p gj_llm < sql/gj-base/auth-schema.sql
mysql -u root -p gj_llm < sql/gj-chat/chat-schema.sql
mysql -u root -p gj_llm < sql/gj-file/file-schema.sql
mysql -u root -p gj_llm < sql/gj-llm-admin/dataset-schema.sql

# 3. 修改各 profile 配置中的连接信息（默认指向本机/内网）
#    application-mybatis.yml   -> MySQL
#    application-redis.yml     -> Redis
#    application-ai.yml        -> Ollama / Milvus / ES / Reranker
#    application-file.yml      -> 文件存储目录

# 4. 拉取模型并部署中间件
ollama pull deepseek-r1:latest  # 对话模型
ollama pull bge-m3:latest       # Embedding 模型
# Elasticsearch 9.x + IK 分词器：见 gj-core/gj-es/ES_INSTALL.md
# Reranker (TEI + BGE-Reranker)：见 gj-core/gj-reranker/RERANKER_INSTALL.md

# 5. 启动后端
mvn clean install -DskipTests
cd gj-llm-start && mvn spring-boot:run

# 6. 启动前端
cd gj-llm-web && pnpm install && pnpm dev
```

前端 `http://localhost:5173`，后端 `http://localhost:8080`。默认账户：`admin` / `111111`。

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    前端 SPA 应用                           │
│          Vue 3 · TypeScript · Vite 6                      │
│    Element Plus · Pinia · 动态路由 · v-permission 指令      │
└──────────────────────────┬──────────────────────────────┘
                           │  REST API  ·  SSE Stream
┌──────────────────────────▼──────────────────────────────┐
│                 Spring Boot 后端服务                       │
│                                                          │
│   ┌───────────┐  ┌───────────┐  ┌───────────┐           │
│   │  对话服务   │  │  RAG 服务  │  │  MCP 服务  │           │
│   │  SSE · 流式 │  │ 知识库·检索 │  │  (规划中)   │           │
│   └───────────┘  └───────────┘  └───────────┘           │
│   ┌─────────────────────────────────────────────────┐    │
│   │  基础管理（RBAC：用户 · 角色 · 菜单 · 接口权限）      │    │
│   │  JWT 双令牌 · 登出黑名单 · 用户缓存                 │    │
│   └─────────────────────────────────────────────────┘    │
│   ┌─────────────────────────────────────────────────┐    │
│   │  基础设施：Security · Redis · MyBatis · File       │    │
│   │            ES 混合检索 · Reranker 精排             │    │
│   └─────────────────────────────────────────────────┘    │
└──────┬──────────┬──────────┬──────────┬──────────┬───────┘
       │          │          │          │          │
  ┌────▼────┐ ┌───▼─────┐ ┌──▼─────┐ ┌─▼──────┐ ┌─▼────────┐
  │  MySQL  │ │ Milvus  │ │   ES   │ │ Redis  │ │  Ollama  │
  │ 业务数据  │ │ 向量集合  │ │混合检索 │ │缓存·黑名单│ │ + TEI精排 │
  └─────────┘ └─────────┘ └────────┘ └────────┘ └──────────┘
```

**对话流程：** 用户输入 → [可选] 查询改写 + HyDE → ES 混合检索（BM25 + KNN + RRF）→ Reranker 精排 → LLM 推理 → SSE 流式返回

**文档入库：** 文件上传 → 异步解析（PDF / Markdown / Office / 文本）→ 递归分块 → 向量化 → ES 索引 + Milvus 集合

**权限校验：** JWT 认证 → Token 黑名单校验 → 加载用户（Redis 缓存）→ 接口拦截器按路径匹配权限点（表记录驱动）

> 检索 / 分块 / 精排的完整原理见 [📚 文档索引](#-文档索引) 中的 RAG 架构文档。

---

## 🛠️ 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 4.1 · Spring AI 2.0 · Spring Security |
| 语言 | Java 25 |
| ORM | MyBatis-Plus 3.5 |
| 关系数据库 | MySQL 8.0+ |
| 向量数据库 | Milvus（HNSW · Cosine） |
| 检索引擎 | Elasticsearch 9.x（BM25 + KNN 混合检索 · IK 中文分词 · RRF 融合） |
| 精排 | BGE-Reranker（TEI · Cross-Encoder） |
| 缓存 | Redis 7.4（Lettuce）-- 用户缓存 · Token 黑名单 |
| 模型服务 | Ollama（DeepSeek-R1 对话 / BGE-M3 嵌入） |
| 认证 | JWT 双令牌（Access + Refresh）+ Redis 登出黑名单 |
| 权限 | RBAC 动态菜单 + 按钮级细粒度（接口拦截器 · 表记录驱动） |
| 前端框架 | Vue 3.5 · TypeScript · Vite 6 |
| UI 组件 | Element Plus |
| 状态管理 | Pinia |

## 🧩 模块结构

项目采用 Maven 多模块 + Vue 3 SPA 前后端分离架构，按「基础设施层 → 业务层 → 启动层」分层：

```
gj-llm/
├── gj-core/                     # 核心基础设施层
│   ├── gj-common/               # 通用工具（JacksonUtils · SpringUtils）
│   ├── gj-security/             # 安全认证（JWT · SecurityUser · 过滤器 · 黑名单接口）
│   ├── gj-mybatis/              # MyBatis-Plus 持久层配置
│   ├── gj-redis/                # Redis 缓存（RedisService · 序列化 · Key 常量）
│   ├── gj-file/                 # 文件存储（上传 / 下载 / 删除）
│   ├── gj-es/                   # Elasticsearch 混合检索（BM25 + KNN + RRF）
│   └── gj-reranker/             # Cross-Encoder 精排（TEI）
├── gj-base-admin/               # 基础管理（RBAC：用户 · 角色 · 菜单 · 接口权限 · 认证）
├── gj-llm-admin/                # 业务模块层
│   ├── gj-llm-chat/             # 对话（SSE 流式 + RAG 编排）
│   ├── gj-llm-rag/              # RAG（知识库 · 文档管道 · 查询改写 · 分块 · 向量管理）
│   └── gj-llm-mcp/              # MCP（规划中）
├── gj-llm-start/                # 启动入口（Spring Boot 聚合）
└── gj-llm-web/                  # 前端 Vue 3 SPA
```

## 📚 文档索引

| 文档 | 说明 |
|------|------|
| [RAG 系统架构](gj-llm-admin/gj-llm-rag/RAG_SYSTEM_ARCHITECTURE.md) | 检索管道、分块策略、精排原理、查询改写、术语表 |
| [Elasticsearch 安装指南](gj-core/gj-es/ES_INSTALL.md) | ES 9.x + IK 中文分词器部署（Docker） |
| [Reranker 安装指南](gj-core/gj-reranker/RERANKER_INSTALL.md) | TEI + BGE-Reranker 精排服务部署 |

---

## 🗺️ 路线图

### 已完成 ✅

- JWT 双令牌认证 + Redis 登出黑名单（登出即时失效）
- 完整 RBAC：动态菜单 + 按钮级细粒度权限（接口拦截器 · 表记录驱动，替代 `@PreAuthorize`）
- 用户 / 角色 / 菜单管理 + 接口自动扫描与权限关联
- 用户登录信息 Redis 缓存 + 事件驱动失效（用户级精确 / 角色级全量）
- SSE 流式对话（thinking / references / content / done 事件）
- 会话管理（创建 / 重命名 / 删除 / 历史消息）
- 知识库 CRUD + 多格式文档解析（PDF / Markdown / Office / 文本，Tika 集成）+ 异步向量化
- ES 混合检索（BM25 + KNN + RRF 融合）+ IK 中文分词
- BGE-Reranker Cross-Encoder 精排
- 查询改写（书面语）+ HyDE 假设文档检索
- 引用溯源（references 事件标注信息来源）
- 文件管理服务（上传 / 下载 / 类型控制）
- 前端动态路由 + v-permission 指令 + 深色 / 浅色主题

### 进行中 🔄

- 文档分块策略优化（语义分块 / 父文档上下文召回）
- 动态 Top-K + 相似度阈值（不相关查询正确返回空）
- IK 自定义词典（领域专有名词匹配）
- 对话导出 / 知识库导入导出
- Prompt 模板管理与模型参数可视化配置
- 查询结果缓存

### 规划中 📋

- **MCP 集成** - MCP Server/Client，连接外部工具扩展模型能力
- **多租户隔离** + 知识库级权限控制
- **多模型支持** - OpenAI / DeepSeek / 通义千问等动态切换
- **企业特性** - 操作审计、用量统计、SSO/OAuth/LDAP
- **容器化部署** - Docker Compose 一键启动
- **接口限流与安全加固**
- **离线评估体系**与用户反馈闭环

---

## 👩‍💻 开发指南

前端使用 ESLint + Prettier 统一代码风格，Commit 遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范，提交前自动执行 lint-staged 检查。

```bash
# 前端（gj-llm-web 目录下）
pnpm dev           # 开发服务器
pnpm build         # 生产构建
pnpm lint          # 代码检查

# 后端（gj-llm-start 目录下）
mvn spring-boot:run  # 启动应用
mvn test             # 运行测试
```

---

## 🤝 贡献

欢迎 Issue 和 PR！请遵循 Conventional Commits 提交规范：

```
feat: 添加新功能
fix: 修复某个问题
docs: 更新文档
refactor: 重构某模块
```

---

## 📄 许可证

MIT License

---

<p align="center">
  <sub>Built with Spring Boot + Vue 3 + Elasticsearch + Milvus + Ollama</sub>
</p>
