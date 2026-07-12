<p align="center">
  <h1 align="center">🤖 GJ-LLM</h1>
  <p align="center">
    <strong>企业级 LLM 知识库平台 —— 基于 RAG 的智能知识管理与对话系统</strong>
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk" />
    <img src="https://img.shields.io/badge/Spring_Boot-4.1-brightgreen?style=flat-square&logo=springboot" />
    <img src="https://img.shields.io/badge/Spring_AI-2.0-green?style=flat-square&logo=spring" />
    <img src="https://img.shields.io/badge/Vue-3.5-blue?style=flat-square&logo=vuedotjs" />
    <img src="https://img.shields.io/badge/Milvus-2.3+-00BEBE?style=flat-square&logo=milvus" />
    <img src="https://img.shields.io/badge/license-MIT-blue?style=flat-square" />
  </p>
</p>

---

## 📖 项目简介

**GJ-LLM** 是一个面向企业的智能知识库平台，深度融合 **大语言模型（LLM）** 与 **检索增强生成（RAG）** 技术。用户上传文档后，系统自动解析、向量化存入知识库，提问时基于文档内容给出精准回答——让 AI 真正「读懂」你的私有知识。

### 核心场景

- 🗣️ **和你的文档对话** — 上传 PDF、Word 等文档，基于文档内容进行智能问答
- 💬 **多轮 AI 对话** — SSE 流式输出，支持思考过程展示，对话中断
- 📚 **知识库管理** — 多知识库独立管理，文档自动向量化，语义检索
- 🔐 **企业级权限** — JWT 认证、用户/角色管理、接口鉴权

---

## 🏗️ 系统架构

```
┌──────────────────────────────────────────┐
│              前端 SPA 应用                 │
│        Vue 3 · TypeScript · Vite          │
│      Element Plus · Pinia · Axios        │
└──────────────────┬───────────────────────┘
                   │  REST API  │  SSE Stream
┌──────────────────▼───────────────────────┐
│           Spring Boot 后端服务             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │  对话服务  │ │  RAG 服务 │ │  MCP 服务 │  │
│  │ 会话·流式  │ │ 知识库·检索│ │  (规划中)  │  │
│  └──────────┘ └──────────┘ └──────────┘  │
│  ┌──────────────────────────────────────┐ │
│  │     基础管理（认证 · 用户 · 角色）      │ │
│  └──────────────────────────────────────┘ │
│  ┌──────────────────────────────────────┐ │
│  │     基础设施（JWT · ORM · 文件存储）    │ │
│  └──────────────────────────────────────┘ │
└──────────────────┬───────────────────────┘
                   │
    ┌──────────────┼──────────────┐
    ▼              ▼              ▼
┌────────┐  ┌───────────┐  ┌──────────┐
│ MySQL  │  │  Milvus   │  │  Ollama  │
│ 业务数据 │  │  向量数据库 │  │ LLM 推理  │
└────────┘  └───────────┘  └──────────┘
```

### 核心流程

**对话流程：** 用户输入 → [可选] 知识库检索 → Prompt 增强 → LLM 推理 → SSE 流式返回

**文档入库：** 文件上传 → 异步解析 → 智能分块 → Embedding 向量化 → 存入 Milvus

---

## 🛠️ 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 4.1 · Spring AI 2.0 · Spring Security |
| 语言 | Java 25 |
| ORM | MyBatis-Plus 3.5 |
| 关系数据库 | MySQL 8.0+ |
| 向量数据库 | Milvus 2.3+ |
| 模型服务 | Ollama（支持 DeepSeek / Qwen 等本地模型） |
| 认证 | JWT 双令牌（Access Token + Refresh Token） |
| 前端框架 | Vue 3 · TypeScript · Vite 6 |
| UI 组件 | Element Plus |
| 状态管理 | Pinia |

---

## 🚀 快速开始

### 环境要求

- **Java** 25+ · **Maven** 3.9+ · **Node.js** 18+ · **pnpm**
- **MySQL** 8.0+ · **Milvus** 2.3+ · **Ollama**

### 启动步骤

```bash
# 1. 克隆项目
git clone https://github.com/BambooTe97/gj-llm.git
cd gj-llm

# 2. 初始化数据库（执行 sql/ 目录下的脚本）
mysql -u root -p gj_llm < sql/gj-base/auth-schema.sql
# ... 依次执行其余脚本

# 3. 修改配置文件中的数据库、Milvus、Ollama 连接信息

# 4. 拉取模型
ollama pull qwen3.5:2b        # 对话模型
ollama pull bge-m3:latest     # Embedding 模型

# 5. 启动后端
mvn clean install -DskipTests
cd gj-llm-start && mvn spring-boot:run

# 6. 启动前端
cd gj-llm-web && pnpm install && pnpm dev
```

前端 `http://localhost:5173`，后端 `http://localhost:8080`。默认账户：`admin` / `111111`。

---

## 🗺️ 路线图

### 已完成 ✅

- JWT 双令牌认证 + 用户/角色权限管理
- SSE 流式对话（Thinking 过程展示、对话中断）
- 会话管理（创建/重命名/删除/历史消息）
- 知识库 CRUD + 文档上传与异步向量化
- Milvus 语义检索 + RAG 增强问答
- 文件管理服务（上传/下载/类型控制）
- 前端深色/浅色主题

### 进行中 🔄

- 更多文档格式支持（Excel / PPT / 扫描 PDF OCR）
- 混合检索（BM25 + 向量语义）
- 文档分块策略优化（语义分块 / 滑动窗口）
- 引用溯源 —— 回答中标注信息来源
- 对话导出 / 知识库导入导出
- Prompt 模板管理与模型参数可视化配置

### 规划中 📋

- **MCP 集成** — MCP Server/Client，连接外部工具扩展模型能力
- **多租户隔离** + 知识库级权限控制
- **多模型支持** — OpenAI / DeepSeek / 通义千问等动态切换
- **企业特性** — 操作审计、用量统计、SSO/OAuth/LDAP
- **容器化部署** — Docker Compose 一键启动
- **接口限流与安全加固**

---

## 👩‍💻 开发指南

项目采用 Maven 多模块 + Vue 3 SPA 的前后端分离架构。前端使用 ESLint + Prettier 统一代码风格，Commit 遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范，提交前自动执行 lint-staged 检查。

```bash
# 前端
pnpm dev           # 开发服务器
pnpm build         # 生产构建
pnpm lint          # 代码检查

# 后端
mvn spring-boot:run  # 启动应用（在 gj-llm-start 目录下）
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
  <sub>Built with Spring Boot + Vue 3 + Milvus + Ollama</sub>
</p>
