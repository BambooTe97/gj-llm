# GJ-LLM Web

基于 Vue 3 + Element Plus + TypeScript 的企业级 LLM 管理平台前端。

## 技术栈

| 层级 | 选型 | 版本 |
|------|------|------|
| 构建工具 | Vite | ^6.3 |
| 前端框架 | Vue 3 (Composition API + `<script setup>`) | ^3.5 |
| UI 组件库 | Element Plus (按需自动导入) | ^2.10 |
| 语言 | TypeScript (strict mode) | ^5.7 |
| 路由 | Vue Router 4 | ^4.5 |
| 状态管理 | Pinia | ^3.0 |
| HTTP 客户端 | Axios | ^1.9 |
| 组合式工具 | @vueuse/core | ^13.1 |
| 图标 | @element-plus/icons-vue | ^2.3 |
| 样式 | SCSS | - |
| 代码规范 | ESLint + Prettier | - |
| 提交规范 | Husky + lint-staged + Commitlint | - |

## 目录结构

```
gj-llm-web/
├── .husky/                      # Git hooks (pre-commit, commit-msg)
├── .vscode/                     # IDE 统一配置
├── public/                      # 静态资源 (不经过构建)
├── src/
│   ├── api/                     # API 接口层
│   │   ├── index.ts             # Axios 实例 + 请求/响应拦截器
│   │   ├── types.ts             # API 类型定义
│   │   └── modules/             # 按业务模块拆分
│   │       ├── auth.ts          # 登录/登出/刷新 Token
│   │       ├── chat.ts          # 对话消息（含 SSE 流式）
│   │       └── conversation.ts  # 会话 CRUD
│   ├── assets/                  # 图片、SVG 等静态资源
│   ├── components/              # 公共组件
│   │   ├── global/              # 全局注册组件
│   │   ├── AppHeader/           # 顶部栏（折叠按钮、用户下拉）
│   │   ├── ChatInput/           # 消息输入区（Enter 发送）
│   │   ├── ChatMessage/         # 消息气泡（用户/AI 双态）
│   │   └── Sidebar/             # 侧边栏（会话列表、新建、删除）
│   ├── composables/             # 组合式函数（逻辑复用）
│   │   ├── useChat.ts           # 对话核心逻辑
│   │   ├── useStream.ts         # SSE 流式接收（Fetch ReadableStream）
│   │   └── useTheme.ts          # 主题切换
│   ├── constants/               # 常量定义（Token 键名、消息角色、HTTP 状态码）
│   ├── directives/              # 自定义指令（v-focus）
│   ├── hooks/                   # 业务 hooks（useLoading）
│   ├── layouts/                 # 布局组件
│   │   ├── DefaultLayout.vue    # 默认布局（侧边栏 + 顶栏 + 内容区）
│   │   └── BlankLayout.vue      # 空白布局（登录页、404、500）
│   ├── plugins/                 # 插件初始化
│   │   └── element-plus.ts      # Element Plus 按需注册
│   ├── router/                  # 路由配置
│   │   ├── index.ts             # 路由实例
│   │   ├── routes.ts            # 路由表
│   │   └── guard.ts             # 路由守卫（Token 校验、标题设置）
│   ├── stores/                  # Pinia 状态管理
│   │   ├── index.ts             # Pinia 实例
│   │   ├── types.ts             # Store 类型定义
│   │   └── modules/
│   │       ├── app.ts           # 全局状态（侧边栏、主题）
│   │       ├── chat.ts          # 对话状态（消息列表、流式状态）
│   │       ├── conversation.ts  # 会话列表状态
│   │       └── user.ts          # 用户状态（Token、登录/登出）
│   ├── styles/                  # 全局样式
│   │   ├── variables.scss       # SCSS 变量（颜色、布局、字体）
│   │   ├── mixins.scss          # SCSS Mixins（flex、省略、滚动条）
│   │   ├── reset.scss           # 浏览器样式重置
│   │   ├── element-variables.scss # Element Plus 主题变量覆盖
│   │   └── global.scss          # 样式入口汇总
│   ├── types/                   # 全局 TypeScript 类型声明
│   │   ├── global.d.ts          # 环境变量类型
│   │   ├── api.d.ts             # API 泛型响应类型
│   │   └── vue.d.ts             # Vue 模块声明
│   ├── utils/                   # 工具函数
│   │   ├── format.ts            # 日期格式化、文本截断
│   │   ├── storage.ts           # localStorage 封装（JSON 序列化）
│   │   └── validate.ts          # 校验工具（手机号、邮箱、空值）
│   ├── views/                   # 页面视图
│   │   ├── chat/ChatView.vue    # 主对话页（消息列表 + 输入区）
│   │   ├── login/LoginView.vue  # 登录页（表单校验）
│   │   ├── settings/SettingsView.vue # 系统设置页
│   │   └── error/               # 错误页（404、500）
│   ├── App.vue                  # 根组件
│   └── main.ts                  # 入口文件
├── .editorconfig                # 编辑器统一配置
├── .env                         # 公共环境变量
├── .env.development             # 开发环境变量
├── .env.production              # 生产环境变量
├── .env.staging                 # 预发环境变量
├── .eslintrc.cjs                # ESLint 配置
├── .gitignore
├── .prettierrc.json             # Prettier 配置
├── commitlint.config.cjs        # Commit 规范配置
├── index.html                   # HTML 入口
├── package.json
├── tsconfig.json                # TypeScript 基础配置
├── tsconfig.app.json            # 应用 TS 配置
├── tsconfig.node.json           # Node 端 TS 配置
└── vite.config.ts               # Vite 配置（别名、代理、自动导入）
```

## 路由设计

| 路径 | 布局 | 页面 | 权限 |
|------|------|------|------|
| `/login` | BlankLayout | 登录页 | 公开 |
| `/` | DefaultLayout | 重定向到 /chat | 需登录 |
| `/chat` | DefaultLayout | 主对话页（默认会话） | 需登录 |
| `/chat/:id` | DefaultLayout | 指定会话的对话页 | 需登录 |
| `/settings` | DefaultLayout | 系统设置页 | 需登录 |
| `/404` | - | 404 页面 | 公开 |
| `/:pathMatch(.*)*` | - | 重定向到 /404 | 公开 |

### 路由守卫流程

```
访问页面 → 检查 Token
  ├── 有 Token + 访问 /login → 重定向到 /chat
  ├── 有 Token + 访问其他 → 放行
  ├── 无 Token + 白名单页面 → 放行
  └── 无 Token + 需登录页 → 重定向到 /login?redirect=原路径
```

## 核心设计

### 请求层 (Axios)

- **请求拦截**：自动注入 `Authorization: Bearer <token>` 头
- **响应拦截**：
  - 解析 `{ code, data, message }` 结构
  - `code !== 200` → ElMessage 错误提示
  - `code === 401` → 清除 Token，跳转登录页
  - 网络超时 / 500 → ElMessage 对应提示

### 流式对话 (SSE)

`useStream.ts` 基于 Fetch API ReadableStream 实现：

1. POST 请求携带 `conversationId` + `content`
2. 响应为 SSE 格式 (`data: {...}\n\n`)
3. 逐块读取 → 解析 `data:` 行 → 追加到 `content`
4. 支持 `AbortController` 中断
5. `data: [DONE]` 表示流结束

### 状态管理 (Pinia)

| Store | 职责 | 关键状态 |
|-------|------|---------|
| `app` | 全局 UI 状态 | `sidebarCollapsed`, `theme` |
| `user` | 用户认证 | `token`, `username`, `login()`, `logout()` |
| `chat` | 对话核心 | `messages`, `streaming`, `sendMessage()` |
| `conversation` | 会话管理 | `list`, `currentId`, `fetchList()`, `create()`, `remove()` |

### 布局系统

- **DefaultLayout** — `el-container` 三栏结构：可折叠侧边栏（260px/64px）+ 固定顶栏（56px）+ 自适应内容区
- **BlankLayout** — 无壳全屏，用于登录页和错误页

## 环境变量

| 变量 | 说明 | 开发环境 | 生产环境 |
|------|------|---------|---------|
| `VITE_API_BASE_URL` | API 基础路径 | `/api` | `/api` |
| `VITE_APP_TITLE` | 页面标题 | `GJ-LLM(Dev)` | `GJ-LLM` |

## 开发命令

```bash
# 安装依赖
npm install

# 启动开发服务器 (默认 http://localhost:5173)
npm run dev

# TypeScript 类型检查 + 生产构建
npm run build

# 预发环境构建
npm run build:staging

# 预览构建产物
npm run preview

# ESLint 检查并自动修复
npm run lint

# Prettier 格式化
npm run format
```

## API 接口规范

### 通用响应格式

```json
{
  "code": 200,
  "data": {},
  "message": "success"
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未授权 / Token 过期 |
| 403 | 无权限 |
| 500 | 服务器错误 |

### 后端需实现的接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录 → `{ token, username, avatar? }` |
| POST | `/api/auth/logout` | 登出 |
| POST | `/api/auth/refresh` | 刷新 Token |
| GET | `/api/conversations` | 会话列表 |
| POST | `/api/conversations` | 创建会话 |
| DELETE | `/api/conversations/:id` | 删除会话 |
| PATCH | `/api/conversations/:id` | 重命名会话 |
| POST | `/api/chat/send` | 发送消息（非流式） |
| POST | `/api/chat/send/stream` | 发送消息（SSE 流式） |
| GET | `/api/chat/messages?conversationId=xx` | 获取历史消息 |

### Vite 代理配置

开发环境下 `/api` 前缀请求自动代理到 `http://localhost:8080`，路径中 `/api` 会被重写去掉。可在 `vite.config.ts` 中 `server.proxy` 修改。

## Commit 规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

| 类型 | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修复 Bug |
| `docs` | 文档变更 |
| `style` | 代码格式（不影响逻辑） |
| `refactor` | 重构 |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建/工具变更 |
| `revert` | 回滚 |
| `build` | 构建系统变更 |
| `ci` | CI 配置变更 |

示例：`feat: 添加对话历史搜索功能`
