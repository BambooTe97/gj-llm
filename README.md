<p align="center">
  <h1 align="center">🤖 GJ-LLM</h1>
  <p align="center">
    <strong>企业级 LLM 知识库平台 —— 基于 RAG 的智能知识管理与对话系统</strong>
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk" alt="Java 25" />
    <img src="https://img.shields.io/badge/Spring_Boot-4.1.0-brightgreen?style=flat-square&logo=springboot" alt="Spring Boot 4.1" />
    <img src="https://img.shields.io/badge/Spring_AI-2.0.0-green?style=flat-square&logo=spring" alt="Spring AI 2.0" />
    <img src="https://img.shields.io/badge/Vue-3.5-blue?style=flat-square&logo=vuedotjs" alt="Vue 3" />
    <img src="https://img.shields.io/badge/Vite-6.3-purple?style=flat-square&logo=vite" alt="Vite 6" />
    <img src="https://img.shields.io/badge/Milvus-2.3+-00BEBE?style=flat-square&logo=milvus" alt="Milvus" />
    <img src="https://img.shields.io/badge/license-MIT-blue?style=flat-square" alt="License" />
  </p>
</p>

---

## 📖 目录

- [项目简介](#-项目简介)
- [核心能力](#-核心能力)
- [系统架构](#-系统架构)
- [项目结构](#-项目结构)
- [技术栈](#-技术栈)
- [快速开始](#-快速开始)
- [API 概览](#-api-概览)
- [路线图](#-路线图)
- [开发指南](#-开发指南)
- [贡献指南](#-贡献指南)

---

## 📖 项目简介

**GJ-LLM** 是一个面向企业的智能知识库平台，深度融合 **大语言模型（LLM）** 与 **检索增强生成（RAG）** 技术，提供智能问答、知识管理、文档检索与流式对话服务。项目采用前后端分离架构，后端基于 **Spring Boot 4.1 + Spring AI 2.0**，前端基于 **Vue 3 + TypeScript**，向量检索使用 **Milvus**，支持本地模型（Ollama）或云端模型（DeepSeek 等）灵活切换。

### 它能做什么？

- 🗣️ **和你的文档对话** — 上传 PDF、Word、Markdown 等文档，系统自动解析、向量化存入知识库，提问时基于文档内容给出精准回答（RAG）
- 💬 **多轮对话** — 类似 ChatGPT 的对话体验，支持 SSE 流式输出、思考过程展示、会话管理
- 📚 **知识库管理** — 创建多个知识库，每个知识库独立管理文档、独立配置模型和检索参数
- 🔐 **企业级权限** — JWT 双令牌认证、用户/角色管理、接口鉴权，开箱即用

---

## ✨ 核心能力

### 已实现

| 模块 | 能力 | 说明 |
|------|------|------|
| **智能对话** | SSE 流式聊天 | 基于 Ollama 的实时流式对话，支持思考链（Thinking）展示 |
| | 多轮会话管理 | 会话的创建、重命名、删除，消息历史持久化 |
| | RAG 增强问答 | 对话中关联知识库，自动检索相关文档片段并注入上下文 |
| | 对话中断 | 支持主动中断正在生成的回复 |
| **知识库** | 知识库 CRUD | 创建、编辑、删除知识库，配置分块参数和向量化策略 |
| | 文档管理 | 支持 PDF、Word、Markdown、TXT、代码文件等 20+ 格式上传 |
| | 文档解析与向量化 | 异步流水线：读取 → 分块 → Embedding → 存入 Milvus |
| | 语义检索 | 基于向量相似度的语义搜索，支持检索测试 |
| | 文档重解析 | 支持对已上传文档重新触发解析和向量化 |
| **认证鉴权** | JWT 双令牌 | Access Token（24h）+ Refresh Token（7d），自动续期 |
| | 用户管理 | 用户的增删改查，状态启用/禁用 |
| | 角色权限 | 角色 CRUD，用户-角色关联，接口基于角色的访问控制 |
| **文件管理** | 文件上传/下载 | 统一的文件存储服务，按日期目录组织，UUID 重命名防冲突 |
| | 文件类型控制 | 可配置的文件扩展名白名单和大小限制 |
| **基础设施** | 通用工具集 | JSON、HTTP、日期、字符串、反射、数字格式化等 15+ 工具类 |
| | 异步任务 | 可配置的异步线程池，支持文档向量化等耗时任务的异步执行 |
| | 统一响应 | 标准化的 ApiResponse 封装，全局异常拦截 |

---

## 🏗️ 系统架构

```
┌────────────────────────────────────────────────────────────┐
│                       gj-llm-web                            │
│                Vue 3 · TypeScript · Vite 6                  │
│            Element Plus · Pinia · Axios · SSE               │
│                      前端 SPA 应用                           │
└──────────────────────────┬─────────────────────────────────┘
                           │  REST API  │  SSE Streaming
┌──────────────────────────▼─────────────────────────────────┐
│                     gj-llm-start                            │
│              Spring Boot 4.1 应用入口                        │
│              统一配置 · 组件扫描 · 自动装配                    │
├────────────────────────────────────────────────────────────┤
│                    gj-llm-admin (聚合)                       │
│  ┌─────────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │   gj-llm-chat   │  │  gj-llm-rag  │  │  gj-llm-mcp  │   │
│  │   对话服务        │  │  知识库/RAG   │  │  MCP (规划中)  │   │
│  │  · 会话管理       │  │  · 数据集管理  │  │              │   │
│  │  · SSE 流式      │  │  · 文档向量化  │  │              │   │
│  │  · 消息持久化     │  │  · 语义检索    │  │              │   │
│  └─────────────────┘  └──────────────┘  └──────────────┘   │
├────────────────────────────────────────────────────────────┤
│                     gj-base-admin                           │
│         AuthController · UserController · RoleController    │
│          认证登录 · 用户管理 · 角色权限管理                     │
├────────────────────────────────────────────────────────────┤
│                       gj-core (聚合)                         │
│  ┌───────────┐ ┌───────────┐ ┌──────────┐ ┌──────────┐    │
│  │ gj-common │ │gj-security│ │gj-mybatis│ │ gj-file  │    │
│  │  公共工具  │ │ JWT 鉴权   │ │ ORM 配置  │ │ 文件管理  │    │
│  │ 15+ 工具类 │ │过滤器·配置 │ │分页·填充  │ │上传·下载  │    │
│  └───────────┘ └───────────┘ └──────────┘ └──────────┘    │
└────────────────────────────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                 ▼
     ┌─────────┐    ┌───────────┐    ┌───────────┐
     │  MySQL  │    │  Milvus   │    │  Ollama   │
     │ 8.0+    │    │  向量数据库  │    │  LLM 服务  │
     │ 业务数据  │    │ 语义检索    │    │ Qwen/DeepSeek│
     └─────────┘    └───────────┘    └───────────┘
```

### 核心流程

**💬 对话流程**

```
用户输入 → 保存用户消息 → [可选] RAG 检索 → 构建 Prompt → Ollama 推理
                                                              ↓
前端 SSE 流式渲染 ← thinking 事件 ← content 事件 ← references 事件
```

**📚 文档入库流程**

```
上传文件 → 保存磁盘 → 创建 dataset_file 记录 → 发布事件
                                                  ↓
                                         异步事件监听（@TransactionalEventListener）
                                                  ↓
                              读取文件内容 → TokenTextSplitter 分块
                                                  ↓
                              Ollama Embedding → Milvus 写入向量
                                                  ↓
                              更新进度 → 记录 segment 元数据 → 完成
```

---

## 📁 项目结构

```
gj-llm/
├── pom.xml                              # 父 POM (Spring Boot 4.1 · Java 25 · Spring AI 2.0)
├── sql/                                 # 数据库初始化脚本
│   ├── gj-base/auth-schema.sql          #   用户/角色/权限表
│   ├── gj-chat/chat-schema.sql          #   会话/消息表
│   ├── gj-llm-admin/dataset-schema.sql  #   知识库/文档/分段表
│   └── gj-file/file-schema.sql          #   文件记录表
├── uploads/                             # 文件上传目录
│
├── gj-core/                             # 核心基础模块（聚合 POM）
│   ├── gj-common/                       #   公共工具 (JSON/HTTP/日期/字符串/反射/数字…)
│   ├── gj-security/                     #   JWT 认证 · Spring Security 过滤器链
│   ├── gj-mybatis/                      #   MyBatis-Plus 配置 · 分页 · 自动填充 · 基础实体
│   └── gj-file/                         #   文件上传/下载/删除 · 静态资源映射
│
├── gj-base-admin/                       # 基础管理模块
│   └── src/main/java/com/gj/llm/base/
│       ├── controller/                  #   AuthController · UserController · RoleController
│       ├── service/                     #   认证 · 用户 · 角色业务逻辑
│       ├── entity/                      #   UserEntity · RoleEntity · UserRoleEntity
│       ├── mapper/                      #   MyBatis Mapper 接口
│       ├── model/                       #   DTO / Request / Response
│       └── handler/                     #   全局异常处理
│
├── gj-llm-admin/                        # LLM 业务模块（聚合 POM）
│   ├── gj-llm-chat/                     #   对话服务
│   │   └── src/main/java/com/gj/llm/chat/
│   │       ├── controller/              #     ChatController (SSE) · ConversationController
│   │       ├── service/                 #     ChatService · ConversationService
│   │       └── entity/                  #     ConversationEntity · MessageEntity
│   ├── gj-llm-rag/                      #   知识库 / RAG 服务
│   │   └── src/main/java/com/gj/llm/rag/
│   │       ├── controller/              #     DatasetController · FileVectorizationController
│   │       ├── service/                 #     DatasetService · DatasetFileService
│   │       ├── reader/                  #     FileContentReader (PDF/Text 策略)
│   │       ├── vectorstore/             #     DynamicVectorStoreManager · MilvusConfig
│   │       └── event/                   #     异步事件驱动向量化
│   └── gj-llm-mcp/                      #   MCP 模块（骨架，待实现）
│
├── gj-llm-start/                        # Spring Boot 启动模块
│   └── src/main/
│       ├── java/…/GjLlmApplication.java #   应用入口
│       └── resources/application.yml    #   全局配置 (DB · JWT · 文件 · AI)
│
└── gj-llm-web/                          # 前端项目
    ├── src/
    │   ├── api/modules/                 #   API 层 (auth · chat · conversation · dataset · file)
    │   ├── components/                  #   通用组件
    │   │   ├── AppHeader/               #     顶部导航栏
    │   │   ├── Sidebar/                 #     侧边栏（会话列表）
    │   │   ├── ChatMessage/             #     消息气泡（支持 Markdown 渲染）
    │   │   ├── ChatInput/               #     输入框（发送 / 中断）
    │   │   └── ChatSubPanel/            #     右侧子面板（会话详情）
    │   ├── composables/                 #   组合式函数 (useChat · useStream · useTheme)
    │   ├── layouts/                     #   布局 (DefaultLayout · BlankLayout)
    │   ├── router/                      #   路由 + 权限守卫
    │   ├── stores/modules/              #   Pinia 状态 (user · chat · conversation · app)
    │   ├── views/                       #   页面
    │   │   ├── chat/                    #     对话页（核心）
    │   │   ├── dataset/                 #     知识库列表 · 详情页
    │   │   ├── login/                   #     登录页
    │   │   ├── settings/                #     设置页
    │   │   └── error/                   #     404 / 500
    │   ├── styles/                      #   全局样式 / SCSS 变量
    │   └── utils/                       #   工具函数 (storage · format · validate)
    ├── .env                             #   环境变量
    ├── vite.config.ts                   #   Vite 配置（含 API 代理）
    └── package.json                     #   依赖管理
```

---

## 🛠️ 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 25 | 运行环境 |
| Spring Boot | 4.1.0 | 应用框架 |
| Spring AI | 2.0.0 | LLM 集成（文档解析、向量存储、Embedding） |
| Spring Security | 7.x | 认证与授权 |
| MyBatis-Plus | 3.5.16 | ORM / 分页 / 自动填充 / 雪花 ID |
| JJWT | 0.12.6 | JWT 令牌签发与校验 |
| MySQL | 8.0+ | 业务数据库（用户/会话/知识库元数据） |
| Milvus | 2.3+ | 向量数据库（文档 Embedding 存储与检索） |
| Ollama | latest | 本地 LLM 推理服务（Chat + Embedding） |
| Fastjson2 | 2.0.62 | JSON 序列化 |
| Yauaa | 8.1.1 | User-Agent 解析 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5 | 前端框架 |
| TypeScript | 5.7 | 类型安全 |
| Vite | 6.3 | 构建工具 |
| Element Plus | 2.10 | UI 组件库 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.5 | 路由管理 |
| Axios | 1.9 | HTTP 客户端 |
| @vueuse/core | 13.1 | 组合式工具集 |
| Sass | 1.86 | CSS 预处理 |
| ESLint + Prettier | — | 代码规范 |
| Husky + Commitlint | — | Git 提交规范 |
| unplugin-auto-import | 19.3 | 自动导入 Composition API |
| unplugin-vue-components | 28.8 | 自动导入 Element Plus 组件 |

### 模型支持

| 模型类型 | 当前使用 | 可替换方案 |
|----------|----------|------------|
| Chat（对话） | `qwen3.5:2b` (Ollama) | `deepseek-r1` · `qwen3:8b` · 任意 OpenAI 兼容 API |
| Embedding（向量化） | `bge-m3:latest` (Ollama) | `text2vec-large-chinese` · `bge-large-zh` |

---

## 🚀 快速开始

### 环境要求

| 工具 | 最低版本 | 说明 |
|------|----------|------|
| Java | 25+ | 运行后端 |
| Maven | 3.9+ | 项目构建 |
| Node.js | 18+ | 运行前端 |
| pnpm | 推荐 | 前端包管理（也可用 npm / yarn） |
| MySQL | 8.0+ | 业务数据库 |
| Milvus | 2.3+ | 向量数据库（RAG 必需） |
| Ollama | latest | 本地模型推理（需拉取 Chat + Embedding 模型） |

### 1. 克隆项目

```bash
git clone https://github.com/BambooTe97/gj-llm.git
cd gj-llm
```

### 2. 初始化数据库

在 MySQL 中创建数据库，并按顺序执行 SQL 脚本：

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS gj_llm DEFAULT CHARACTER SET utf8mb4;"

# 执行初始化脚本
mysql -u root -p gj_llm < sql/gj-base/auth-schema.sql
mysql -u root -p gj_llm < sql/gj-chat/chat-schema.sql
mysql -u root -p gj_llm < sql/gj-llm-admin/dataset-schema.sql
mysql -u root -p gj_llm < sql/gj-file/file-schema.sql
```

### 3. 配置后端

编辑 `gj-llm-start/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://<your-mysql-host>:3306/gj_llm
    username: <your-username>
    password: <your-password>

# JWT 密钥（生产环境务必修改为安全的 Base64 密钥）
app:
  security:
    jwt:
      secret: <your-base64-secret>
```

编辑 AI 配置 `gj-llm-admin/gj-llm-rag/src/main/resources/application-ai.yml`：

```yaml
spring:
  ai:
    ollama:
      base-url: http://<your-ollama-host>:11434
      chat:
        options:
          model: qwen3.5:2b     # 按需修改
      embedding:
        options:
          model: bge-m3:latest   # 按需修改
    vectorstore:
      milvus:
        host: <your-milvus-host>
        port: 19530
```

### 4. 拉取模型

```bash
# 拉取对话模型
ollama pull qwen3.5:2b

# 拉取 Embedding 模型
ollama pull bge-m3:latest
```

### 5. 启动后端

```bash
# 编译项目
mvn clean install -DskipTests

# 启动应用
cd gj-llm-start
mvn spring-boot:run
```

后端启动后，API 默认运行在 `http://localhost:8080`。

### 6. 启动前端

```bash
cd gj-llm-web

# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev
```

前端开发服务器运行在 `http://localhost:5173`，API 请求自动代理到后端。

### 默认账户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `111111` | 管理员（ADMIN） |

> ⚠️ **生产环境请务必修改默认密码！**

---

## 📡 API 概览

### 认证接口 `/api/auth`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/login` | 用户登录，返回 Access + Refresh Token | ❌ |
| POST | `/api/auth/refresh` | 刷新 Access Token | Refresh Token |
| POST | `/api/auth/logout` | 登出（当前为无状态） | Access Token |

### 用户管理 `/api/users`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/users` | 获取所有用户（含角色信息） |
| GET | `/api/users/{id}` | 获取单个用户详情 |
| POST | `/api/users` | 创建用户 |
| PUT | `/api/users/{id}` | 更新用户（支持角色分配） |
| DELETE | `/api/users/{id}` | 删除用户 |

### 角色管理 `/api/roles`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/roles` | 获取所有角色 |
| POST | `/api/roles` | 创建角色 |
| DELETE | `/api/roles/{id}` | 删除角色 |

### 文件管理 `/api/files`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/files/upload` | 上传文件 |
| GET | `/api/files` | 分页获取文件列表 |
| GET | `/api/files/{id}` | 下载/预览文件 |
| DELETE | `/api/files/{id}` | 删除文件 |

### 对话服务 `/api/v1/chat`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/chat/send/stream` | 发送消息（SSE 流式响应） |

SSE 事件类型：

| 事件 | 说明 |
|------|------|
| `thinking` | 模型的思考/推理过程 |
| `references` | RAG 检索到的参考文档片段 |
| `content` | 模型生成的回答内容 |
| `done` | 对话完成（含完整消息元数据） |
| `error` | 错误信息 |

### 会话管理 `/api/v1/conversations`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/conversations` | 获取当前用户的会话列表 |
| POST | `/api/v1/conversations` | 创建新会话 |
| PATCH | `/api/v1/conversations/{id}` | 重命名会话 |
| DELETE | `/api/v1/conversations/{id}` | 删除会话（逻辑删除） |
| GET | `/api/v1/conversations/{id}/messages` | 获取会话消息历史 |

### 知识库管理 `/api/v1/datasets`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/datasets` | 分页获取知识库列表 |
| GET | `/api/v1/datasets/{id}` | 获取知识库详情 |
| POST | `/api/v1/datasets` | 创建知识库 |
| PUT | `/api/v1/datasets/{id}` | 更新知识库配置 |
| DELETE | `/api/v1/datasets/{id}` | 删除知识库 |
| POST | `/api/v1/datasets/{id}/documents/upload` | 上传文档到知识库 |
| GET | `/api/v1/datasets/{id}/documents` | 获取知识库文档列表 |
| DELETE | `/api/v1/datasets/{id}/documents/{dfId}` | 删除文档（含向量） |
| POST | `/api/v1/datasets/{id}/documents/{dfId}/reparse` | 重新解析文档 |
| POST | `/api/v1/datasets/{id}/test` | RAG 检索测试 |

---

## 🗺️ 路线图

### Phase 1 ✅ 已完成

- [x] 项目 Maven 多模块脚手架
- [x] JWT 双令牌认证体系（Access + Refresh Token）
- [x] 用户 / 角色权限管理
- [x] SSE 流式对话（支持 Thinking 过程展示）
- [x] 会话管理（创建 / 重命名 / 删除 / 消息历史）
- [x] 知识库 CRUD
- [x] 文档上传与异步向量化（PDF / Word / Markdown / TXT 等）
- [x] Milvus 向量存储与语义检索
- [x] RAG 对话链路（检索 → 增强 → 生成）
- [x] 文件管理服务（上传 / 下载 / 类型控制）
- [x] 全局异常处理与统一响应
- [x] 前端深色/浅色主题切换
- [x] 对话中断功能

### Phase 2 🔄 功能增强

- [ ] 更多文档格式支持（Excel / PPT / HTML 表格 / 扫描 PDF OCR）
- [ ] 混合检索（BM25 关键词 + 向量语义混合）
- [ ] 文档分块策略优化（语义分块 / 滑动窗口 / 层级分块）
- [ ] RAG 检索结果重排序（Reranker）
- [ ] 引用溯源 —— 回答中标注信息来源段落
- [ ] 对话导出（Markdown / PDF）
- [ ] 知识库导入导出
- [ ] Prompt 模板管理与自定义
- [ ] 模型参数可视化配置（Temperature / Top-P / Max Tokens）

### Phase 3 📋 MCP 集成

- [ ] MCP Server 实现 —— 将平台能力注册为 MCP 工具
- [ ] MCP Client 集成 —— 连接外部 MCP Server 扩展模型能力
- [ ] 内置工具集（数据库查询 / 文件操作 / 网络请求）
- [ ] 工具调用链可视化与调试

### Phase 4 📋 企业特性

- [ ] 多租户数据隔离
- [ ] 操作审计日志（谁在什么时候做了什么）
- [ ] Token 用量统计与分析
- [ ] 多模型支持与动态切换（OpenAI / DeepSeek / 通义千问 等）
- [ ] 知识库级权限控制（只读 / 读写 / 管理员）
- [ ] 单点登录（SSO / OAuth 2.0 / LDAP）
- [ ] 容器化部署（Docker Compose 一键启动）
- [ ] 消息队列替代异步事件（RabbitMQ / Kafka）
- [ ] 接口限流与安全加固

---

## 👩‍💻 开发指南

### 项目约定

- **包命名**：`com.gj.llm.<module>` —— 按模块划分顶层包
- **API 版本**：`/api/v1/` 前缀用于 LLM 业务接口，`/api/` 用于基础管理接口
- **统一响应**：所有接口使用 `ApiResponse<T>` 封装（code + message + data）
- **数据库 ID**：使用 MyBatis-Plus 雪花算法（`IdType.ASSIGN_ID`）生成分布式唯一 ID
- **代码规范**：前端 ESLint + Prettier，后端遵循 Java 标准命名约定

### 常用命令

```bash
# ========== 前端 ==========
cd gj-llm-web

pnpm dev              # 启动开发服务器（默认 http://localhost:5173）
pnpm build            # 构建生产版本
pnpm build:staging    # 构建预发布版本
pnpm lint             # ESLint 代码检查
pnpm format           # Prettier 代码格式化

# ========== 后端 ==========
mvn clean install -DskipTests    # 编译安装（跳过测试）
mvn spring-boot:run              # 启动应用（需在 gj-llm-start 目录下）
mvn test                         # 运行测试
```

### 添加新模块

项目采用 Maven 多模块结构，添加新模块的步骤：

1. 创建模块目录（如 `gj-llm-admin/gj-llm-xxx`）
2. 编写 `pom.xml`，继承父 POM
3. 在父 POM（`gj-llm-admin/pom.xml`）中声明 `<module>`
4. 在 `gj-llm-start/pom.xml` 中添加依赖，使启动模块能扫描到新模块的组件

### Commit 规范

项目使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范，提交前自动执行 lint-staged 检查：

```
feat: 添加知识库文档重解析功能
fix: 修复 SSE 连接中断后消息丢失的问题
refactor: 重构向量存储管理器
docs: 更新 README 中的 API 文档
style: 调整对话页消息间距
```

---

## 🤝 贡献指南

欢迎贡献！请遵循以下流程：

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feat/amazing-feature`
3. 提交代码：`git commit -m 'feat: add amazing feature'`
4. 推送到远程：`git push origin feat/amazing-feature`
5. 提交 Pull Request

### 提交前检查清单

- [ ] 代码通过 ESLint / 编译检查
- [ ] 新功能包含必要的注释
- [ ] 更新了相关的 API 文档或配置说明
- [ ] Commit 信息符合 Conventional Commits 规范

---

## 📄 许可证

本项目基于 MIT 许可证开源。

---

<p align="center">
  <sub>Built with ❤️ using Spring Boot + Vue 3 + Milvus + Ollama</sub>
</p>
