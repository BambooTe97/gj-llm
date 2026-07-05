# GJ-LLM

<p align="center">
  <strong>🚀 企业级 LLM 知识库平台 —— 支持 RAG、MCP 的智能知识管理系统</strong>
</p>

## 📖 项目简介

GJ-LLM 是一个面向企业的智能知识库平台，深度融合**大语言模型（LLM）**能力，提供基于 **RAG（检索增强生成）** 的智能问答、知识检索与对话服务。项目计划支持 **MCP（Model Context Protocol）**，实现模型与外部工具/数据源的标准化集成。

### 核心能力

- **💬 智能对话** —— 基于 LLM 的多轮对话，支持 SSE 流式响应
- **📚 知识库管理** —— 文档导入、解析、向量化存储与语义检索（RAG）
- **🔌 MCP 集成** —— 通过 Model Context Protocol 连接外部工具与数据源
- **🔐 企业级认证** —— JWT 认证、用户/角色权限管理
- **🎨 现代化 UI** —— Vue 3 + Element Plus 响应式管理后台

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────┐
│                  gj-llm-web                  │
│          Vue 3 + TypeScript + Vite           │
│        Element Plus · Pinia · Axios          │
│              前端管理后台 / 对话界面             │
└──────────────────┬──────────────────────────┘
                   │ REST API / SSE
┌──────────────────▼──────────────────────────┐
│               gj-llm-admin                   │
│        Spring Boot 4.1 · Java 25             │
│          应用入口 · 自动装配 · 监控             │
├─────────────────────────────────────────────┤
│               gj-base-admin                  │
│      认证服务 · 用户管理 · 角色权限管理          │
│       Spring Security · JWT · MyBatis-Plus   │
├─────────────────────────────────────────────┤
│                 gj-core                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐ │
│  │gj-common │ │gj-security│ │  gj-mybatis   │ │
│  │ 公共工具  │ │ JWT 鉴权  │ │MyBatis-Plus   │ │
│  │          │ │Spring Sec │ │分页·自动填充   │ │
│  └──────────┘ └──────────┘ └──────────────┘ │
│  ┌──────────────────────────────────────────┐│
│  │              gj-file                      ││
│  │     文件管理 · 上传/下载 · 本地存储         ││
│  └──────────────────────────────────────────┘│
├─────────────────────────────────────────────┤
│              Spring AI 2.0                   │
│   DeepSeek · Ollama · Milvus · 文档解析       │
│      （RAG 引擎 · 向量检索 · LLM 调用）        │
└─────────────────────────────────────────────┘
```

## 📁 项目结构

```
gj-llm/
├── pom.xml                          # 父 POM（Spring Boot 4.1, Java 25, Spring AI 2.0）
├── gj-core/                         # 核心模块
│   ├── gj-common/                   #   公共工具（SecurityUtils 等）
│   ├── gj-security/                 #   JWT 认证、Spring Security 配置
│   ├── gj-mybatis/                  #   MyBatis-Plus ORM 配置
│   └── gj-file/                     #   文件管理模块
├── gj-base-admin/                   # 基础管理模块
│   └── src/main/java/com/gj/llm/admin/
│       ├── controller/              #   REST API 控制器（Auth/User/Role）
│       ├── service/                 #   业务服务层
│       ├── entity/                  #   数据实体（User/Role/UserRole）
│       ├── mapper/                  #   MyBatis Mapper
│       └── model/                   #   DTO / 请求响应模型
├── gj-llm-admin/                    # 应用启动模块
│   └── src/main/java/com/gj/llm/
│       └── GjLlmApplication.java   #   Spring Boot 入口
└── gj-llm-web/                      # 前端项目
    ├── src/
    │   ├── api/                     #   API 接口层（chat/auth/conversation）
    │   ├── components/              #   通用组件（ChatMessage/Sidebar/AppHeader）
    │   ├── composables/             #   组合式函数（useChat/useTheme）
    │   ├── layouts/                 #   布局组件
    │   ├── router/                  #   路由配置 + 权限守卫
    │   ├── stores/                  #   Pinia 状态管理
    │   ├── styles/                  #   全局样式 / SCSS 变量
    │   ├── types/                   #   TypeScript 类型定义
    │   ├── utils/                   #   工具函数
    │   └── views/                   #   页面视图
    │       ├── chat/                #     对话页面
    │       ├── settings/            #     设置页面
    │       ├── login/               #     登录页面
    │       └── error/               #     错误页面（404/500）
    ├── .env                         #   环境变量
    ├── vite.config.ts               #   Vite 配置
    └── package.json                 #   依赖管理
```

## 🚀 快速开始

### 环境要求

| 工具 / 环境 | 版本要求             |
|------------|---------------------|
| Java       | 25+                 |
| Maven      | 3.9+                |
| Node.js    | 18+                 |
| MySQL      | 8.0+                |
| Milvus     | 2.3+（RAG 向量存储）   |
| pnpm       | 推荐（或 npm/yarn）    |

### 后端启动

```bash
# 1. 克隆项目
git clone <repo-url>
cd gj-llm

# 2. 配置数据库
# 编辑 gj-llm-admin/src/main/resources/application.yml
# 配置 MySQL 连接信息

# 3. 编译并启动
mvn clean install -DskipTests
cd gj-llm-admin
mvn spring-boot:run
```

### 前端启动

```bash
# 1. 进入前端目录
cd gj-llm-web

# 2. 安装依赖
pnpm install

# 3. 启动开发服务器
pnpm dev

# 4. 构建生产版本
pnpm build
```

前端开发服务器默认运行在 `http://localhost:5173`，后端 API 代理至 `/api`。

### 环境变量

前端环境变量位于 `gj-llm-web/` 目录下：

| 文件                  | 用途           |
|----------------------|---------------|
| `.env`               | 通用配置        |
| `.env.development`   | 开发环境        |
| `.env.staging`       | 预发布环境       |
| `.env.production`    | 生产环境        |

主要变量：

```bash
VITE_API_BASE_URL=/api    # API 接口前缀
VITE_APP_TITLE=GJ-LLM     # 应用标题
```

## 🧩 规划路线图

### Phase 1 · 已完成 / 进行中 ✅

- [x] 项目脚手架搭建（Maven 多模块 + Vue 3 工程）
- [x] 用户认证体系（JWT + Spring Security）
- [x] 用户/角色权限管理
- [x] 基础对话界面（SSE 流式响应）
- [x] 会话管理（创建/删除/重命名）
- [x] MyBatis-Plus ORM 集成

### Phase 2 · RAG 知识库 🔄

- [ ] 文档导入与解析（PDF / Markdown / Word / HTML）
- [ ] 文档切片与向量化（Embedding）
- [ ] Milvus 向量数据库集成
- [ ] 语义检索与混合搜索
- [ ] RAG 问答链路（检索 → 增强 → 生成）
- [ ] 知识库管理后台

### Phase 3 · MCP 集成 📋

- [ ] MCP Server 实现（工具注册与调用）
- [ ] MCP Client 集成（连接外部工具）
- [ ] 内置工具集（文件操作 / 数据库查询 / API 调用）
- [ ] 工具调用链可视化

### Phase 4 · 企业特性 📋

- [ ] 多租户隔离
- [ ] 操作审计日志
- [ ] 用量统计与计费
- [ ] 模型切换与配置管理
- [ ] 知识库权限控制
- [ ] 导入导出 / 数据迁移

## 🛠️ 技术栈

### 后端

| 技术                   | 用途                       |
|-----------------------|---------------------------|
| Spring Boot 4.1       | 应用框架                    |
| Spring AI 2.0         | LLM 集成框架（RAG/向量存储）   |
| Spring Security       | 认证与授权                  |
| MyBatis-Plus 3.5      | ORM / 数据库操作            |
| JJWT 0.12             | JWT 令牌管理                |
| MySQL 8.0             | 关系型数据库                 |
| Milvus                | 向量数据库（RAG）            |
| DeepSeek / Ollama     | 大语言模型                   |

### 前端

| 技术              | 用途              |
|------------------|------------------|
| Vue 3            | 前端框架           |
| TypeScript       | 类型安全           |
| Vite 6           | 构建工具           |
| Element Plus     | UI 组件库          |
| Pinia            | 状态管理           |
| Vue Router       | 路由管理           |
| Axios            | HTTP 客户端        |
| Sass             | CSS 预处理         |
| ESLint + Prettier| 代码规范           |
| Husky + Commitlint| Git 提交规范       |

## 🤝 开发指南

### 代码规范

- 前端使用 ESLint + Prettier 统一代码风格
- Commit 信息遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范
- Git 提交前自动执行 lint-staged 检查

### 常用命令

```bash
# 前端
pnpm dev              # 启动开发服务器
pnpm build            # 构建生产版本
pnpm build:staging    # 构建预发布版本
pnpm lint             # 代码检查
pnpm format           # 代码格式化

# 后端
mvn clean install     # 编译安装
mvn spring-boot:run   # 启动应用
mvn test              # 运行测试
```

---

<p align="center">
  <sub>Built with ❤️ by GJ-LLM Team</sub>
</p>
