# MCP 模块系统架构

> gj-llm-mcp —— Model Context Protocol 能力模块：把企业知识库「送出去」（MCP Server），把外部工具「引进来」（MCP Client），并以企业级硬能力（认证 / 数据隔离 / 审计 / 动态管理 / 稳定性）为底座。

## 一、定位与总体架构

MCP（Model Context Protocol）是连接 LLM 应用与外部能力的开放协议。本模块提供**双向**能力：

```
                          ┌─────────────────────────────────────────┐
                          │              gj-llm 平台                 │
                          │                                         │
 外部 MCP 客户端           │  ┌───────────────┐   ┌───────────────┐  │
 (Claude Desktop/Cursor/  │  │  MCP Server   │   │  MCP Client   │  │
  内部 Agent 平台) ────────┼─▶│  知识库工具暴露  │   │  外部工具接入   │  │
        API Key 认证       │  └───────┬───────┘   └───────┬───────┘  │
                          │          │                   │          │
                          │   ┌──────▼───────────────────▼───────┐  │
                          │   │  横切底座: API Key 认证 · 可见域隔离 │  │
                          │   │  审计 · 凭据加密 · 健康检查 · RBAC   │  │
                          │   └──────────────────────────────────┘  │
                          └───────┬─────────────────────┬───────────┘
                                  │                     │
                          ┌───────▼───────┐     ┌───────▼───────────┐
                          │  rag 检索门面   │     │  外部 MCP Server   │
                          │ (可见域过滤后)  │     │ (搜索/数据库/内部系统)│
                          └───────────────┘     └───────────────────┘
```

- **Server 方向（送出去）**：把平台知识库检索能力按 MCP 协议暴露，外部 MCP 客户端凭 API Key 直连查询，知识库从「只能进网页问」变成「任何 AI 应用都能接」。
- **Client 方向（引进来）**：平台作为 MCP 客户端连接外部工具服务器，把工具注入对话；chat 模块新增工具循环智能体，让模型自主决定何时检索、何时调外部工具。

**依赖方向**：`gj-llm-chat → gj-llm-mcp → gj-llm-rag`，与既有 `chat → rag` 一致，无环。

## 二、一期范围（企业级硬能力，全部本期落地）

| # | 能力 | 说明 |
|---|------|------|
| 1 | API Key 认证 | `mcp_api_key` 表；只存 SHA-256 哈希 + 前缀明文；发放/吊销/启停/过期 |
| 2 | 数据可见域隔离 | Key → 用户身份 → 复用 rag `DatasetVisibleService`，fail-closed |
| 3 | 凭据加密 | 外部 server 的 auth 凭据 AES-GCM 加密落库，密钥走配置 |
| 4 | 审计日志 | 双向工具调用全量审计（谁/何时/什么工具/参数摘要/结果/耗时/IP），异步落库不阻塞主链路 |
| 5 | 动态管理 | 外部 server DB 配置驱动，CRUD/启停/连接测试/工具预览，**运行时生效不重启** |
| 6 | 稳定性 | 超时控制、定时健康检查、断线重连、失败隔离（单 server 故障不影响其他工具与主对话）、降级兜底 |
| 7 | 工具治理 | Server 级启停开关 + 全局外部工具总开关，一键止血 |
| 8 | RBAC | `mcp:*` 权限点 + 菜单 SQL + ApiAutoLinker 自动关联 |
| 9 | 双向基础能力 | Server：search_knowledge / list_datasets 工具；Client：外部工具聚合注入；chat 工具循环智能体 |

二期（优化与增强，允许延后）：MCP Resources/Prompts 暴露、工具调用配额与限流、审计报表与告警、工具发现缓存调优、前端可视化增强。

## 三、数据库设计（sql/gj-llm-admin/mcp-schema.sql）

### mcp_api_key（API Key 表）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 雪花主键 |
| user_id | BIGINT NOT NULL | 绑定用户（可见域判定主体） |
| username | VARCHAR(64) | 用户名快照（审计展示用，免跨模块联查） |
| name | VARCHAR(64) | Key 用途名称 |
| key_prefix | VARCHAR(16) | Key 前缀明文（辨识用，如 `mcp_Ab3dEf…`） |
| key_hash | VARCHAR(64) UNIQUE | SHA-256 十六进制（**不存明文**，发放时仅返回一次） |
| status | TINYINT | 1=启用 0=停用 |
| expires_at | DATETIME NULL | 过期时间（NULL=永不过期） |
| last_used_at | DATETIME | 最近使用时间 |
| create_by / update_by / created_at / updated_at | | BaseEntity 自动填充 |

### mcp_server_config（外部 MCP Server 配置表）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 雪花主键 |
| name | VARCHAR(64) UNIQUE | 连接标识 |
| transport | VARCHAR(20) | STREAMABLE_HTTP / SSE |
| endpoint | VARCHAR(512) | 完整 URL（如 `http://host:port/mcp`） |
| auth_header_name | VARCHAR(64) NULL | 认证头名（如 `Authorization`） |
| auth_header_value | VARCHAR(1024) NULL | 认证头值（**AES-GCM 加密存储**） |
| enabled | TINYINT | 1=启用 0=停用（启停运行时生效） |
| health_status | VARCHAR(16) | UP / DOWN / UNKNOWN |
| last_healthy_at | DATETIME | 最近健康时间 |
| tool_count | INT | 工具数量（最近一次发现） |
| remark | VARCHAR(255) | 备注 |

### mcp_audit_log（审计日志表）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 雪花主键 |
| direction | VARCHAR(16) | SERVER_CALLED（server 被调）/ CLIENT_CALL（client 出调） |
| user_id / username | | 调用方（username 快照） |
| api_key_id | BIGINT NULL | server 被调时的 Key |
| server_name | VARCHAR(64) NULL | client 出调时的目标 server |
| tool_name | VARCHAR(128) | 工具名 |
| params_digest | VARCHAR(1024) | 入参摘要（截断） |
| result_status | VARCHAR(16) | SUCCESS / ERROR / TIMEOUT |
| error_message | VARCHAR(512) NULL | 失败原因 |
| cost_ms | BIGINT | 耗时 |
| client_ip | VARCHAR(64) | 来源 IP |
| created_at | DATETIME | 索引列 |

索引：`idx_audit_time(created_at)`、`idx_audit_user(user_id)`、`idx_audit_tool(tool_name)`、`idx_audit_status(result_status)`。

## 四、模块结构

```
com.gj.llm.mcp
├── constant/McpConstants.java          # 方向/状态/传输类型/健康状态常量
├── config/
│   ├── McpProperties.java              # gj.llm.mcp.* 配置（server/client/tool 超时/加密/审计）
│   └── McpScheduleConfig.java          # @EnableScheduling（全仓首个，健康检查用）
├── common/AesGcmTextCipher.java        # AES-GCM 加解密（IV 随机前置，Base64）
├── auth/
│   ├── McpUserContext.java             # ThreadLocal 用户上下文（server 被调链路）
│   ├── McpApiKeyAuthInterceptor.java   # /open/mcp/** 校验 X-Api-Key / Bearer
│   └── McpWebConfig.java               # 拦截器注册
├── entity/                             # McpApiKeyEntity / McpServerConfigEntity / McpAuditLogEntity
├── mapper/                             # 三个 BaseMapper
├── event/McpToolCallEvent.java         # 工具调用事件（record）
├── listener/McpAuditEventListener.java # @Async @EventListener → 异步落库
├── service/                            # McpApiKeyService / McpServerConfigService / McpAuditService
│   └── impl/                           # 对应实现
├── server/                             # ===== MCP Server 端 =====
│   ├── McpServerToolProvider.java      # ToolCallbackProvider Bean（starter 自动收集）
│   ├── AuditedToolCallback.java        # 被调审计装饰器
│   └── tool/
│       ├── KnowledgeSearchTool.java    # @Tool search_knowledge
│       └── DatasetListTool.java        # @Tool list_datasets
├── client/                             # ===== MCP Client 端 =====
│   ├── McpConnectionManager.java       # 连接生命周期（自建，不走 starter 自动装配）
│   ├── McpToolRegistry.java            # 外部工具聚合 → ToolCallback[]（供 chat 注入）
│   ├── McpHealthChecker.java           # 定时健康检查 + 自动重连
│   └── ToolCallSpyCallback.java        # 出调装饰器（SSE 事件 + 审计）
└── controller/                         # 管理 API（/api/v1/mcp/**，统一 R<T> 包装）
    ├── McpApiKeyController.java
    ├── McpServerConfigController.java
    └── McpAuditController.java
```

## 五、关键设计

### 5.1 API Key 认证链（Server 被调方向）

```
外部 MCP 客户端 ──▶ /open/mcp/**（Security permitAll + ApiPermissionInterceptor 排除）
                ──▶ McpApiKeyAuthInterceptor
                      ① 取 X-Api-Key 或 Authorization: Bearer <key>
                      ② SHA-256(key) → 查 mcp_api_key.key_hash（唯一索引）
                      ③ 校验 status=1、未过期 → McpUserContext.set(userId/username/apiKeyId/ip)
                      ④ 异步触摸 last_used_at
                      ⑤ 失败一律 401（不区分"key 不存在/停用/过期"，防探测）
                ──▶ MCP 端点处理 → 工具执行时从 McpUserContext 取身份
```

- afterCompletion 清理 ThreadLocal，防线程池串号。
- MCP 客户端没有 JWT 登录流程，故走静态 Key + 独立拦截器，不侵入现有 JWT 链。

### 5.2 数据可见域隔离（复用既有护栏，fail-closed）

```
工具执行 → userId = McpUserContext.get()（server 被调）
              或 ToolContext("userId")（chat 工具循环，见 5.5）
         → DatasetVisibleService.visibleDatasetIds(userId)   // PUBLIC∪自建∪授权，管理员全量
         → RetrievalService.retrieve(query, 可见集, userId)   // 检索门面二次求交
```

双重防线：工具侧先求可见集，`RetrievalService` 内部再做一次可见域交集，任一层失效都不会越权。

### 5.3 MCP Server 端

- 依赖 `spring-ai-starter-mcp-server-webmvc`（Spring AI 2.0 BOM 已管理），传输协议 STREAMABLE HTTP，端点 `/open/mcp`。
- 工具注册：`McpServerToolProvider` 提供 `ToolCallbackProvider` Bean（内部 `MethodToolCallbackProvider` 包装两个 @Tool 对象并叠加审计装饰器），starter 自动收集该 Bean 暴露为 MCP 工具。
- chat 侧的工具**不以 Bean 形式存在**（逐请求经装饰器传入 ChatClient），不会被 server starter 误收集。

| 工具 | 签名 | 说明 |
|---|---|---|
| search_knowledge | `query*(string), top_k(int, 默认5), dataset_id(long 可选)` | 检索可见知识库；返回引用 JSON（rank/content/source/datasetName/score），内容超长截断 |
| list_datasets | 无参 | 列出当前 Key 用户可见的知识库（pageForUser） |

### 5.4 MCP Client 端（自建连接管理，不用 starter 自动装配）

starter 自动装配是「配置文件驱动、启动时一次性建连」，满足不了企业要求的运行时动态管理。因此直接使用 MCP Java SDK（`io.modelcontextprotocol.sdk:mcp-core`）自建：

```
McpServerConfigServiceImpl(启停/测试/CRUD)
        │
        ▼
McpConnectionManager（synchronized 管理生命周期）
  connect(cfg):  按 transport 构建传输层
                 STREAMABLE_HTTP → HttpClientStreamableHttpTransport.builder(base).endpoint(path)
                 SSE             → HttpClientSseClientTransport.builder(base).sseEndpoint(path)
                 + McpSyncHttpClientRequestCustomizer 注入认证头（值经 AES 解密）
                 McpClient.sync(transport).requestTimeout(...).clientInfo(...).build().initialize()
  disconnect / restart / ping / listTools / connectedEnabled()
        │
        ▼
McpToolRegistry（缓存聚合）
  ToolCallback[] externalCallbacks() = SyncMcpToolCallbackProvider(已启用已连接的 clients)
  配置变更时 invalidate()，chat 侧每次请求取最新
        │
        ▼
McpHealthChecker（@Scheduled 定时）
  ping 所有启用连接 → UP 更新 last_healthy_at；DOWN 尝试 restart 一次 → 更新 health_status
  失败隔离：单连接故障只影响自身状态，不抛出到其他链路
```

### 5.5 chat 工具循环（ToolCallAgent）

现状：`AbstractLlmAgent` 是「固定前置检索」模板（prepare → streamLlm），无工具循环。改造为**双钩子扩展**，不破坏既有智能体：

```java
// AbstractLlmAgent 新增两个 protected 钩子（默认空实现）
protected ChatClientRequestSpec customizeRequest(spec, ctx)   // 子类注入工具
protected Flux<ServerSentEvent<String>> sideChannel(ctx)      // 子类提供旁路事件流
// stream(): Flux.concat(preEvents, Flux.merge(llm, sideChannel(ctx)))
```

`ToolCallAgent`（id=`tool-call`，独立于 rag-qa/chitchat，路由可配）：

1. **工具集合**：内置 `KnowledgeSearchTool`（模型自主决定何时检索，替代固定前置）+ `McpToolRegistry` 的外部工具。
2. **逐请求装饰**：所有工具包上 `ToolCallSpyCallback`——调用前发 `tool_call` SSE、调用后发 `tool_result` SSE、发布 CLIENT_CALL 审计事件。
3. **用户身份传递**：工具执行可能发生在 reactor 线程（SecurityContext ThreadLocal 不可靠），chat 链路经 `.toolContext(Map.of("userId", ...))` 显式下传；server 被调链路经 `McpUserContext`。工具内部统一解析。
4. **SSE 协议扩展**：新增 `tool_call` / `tool_result` 事件（与 thinking/references/content 同风格），前端可展示「正在调用 xx 工具」。
5. **降级兜底**：工具抛错不中断对话——spy 捕获后发错误 tool_result 事件，LLM 继续生成。

超时控制：外部工具由 MCP 客户端 `requestTimeout` 保证；内置检索由检索管线自身超时保证。

### 5.6 审计

- `McpToolCallEvent`（record）由两端装饰器发布 → `McpAuditEventListener`（`@Async @EventListener`，复用 gj-common `AsyncThreadPoolConfig` 线程池）异步落库，主链路零阻塞。
- 查询 API：`GET /api/v1/mcp/audits` 分页 + user/tool/status/direction/时间过滤。

### 5.7 凭据加密

- `AesGcmTextCipher`：AES/GCM/NoPadding，随机 12 字节 IV 前置密文，Base64 编码。
- 密钥来源 `gj.llm.mcp.crypto.secret`（Base64 32 字节）；未配置时开发环境降级为内置默认密钥并 WARN（**生产必须配置**）。
- 更新配置时请求传掩码（`******`）或空即保留原值，避免密文回显泄露。

## 六、RBAC 权限设计

- 端点管理 API 走既有表驱动拦截器：`ApiScanner` 启动自动扫 `sys_api`；`ApiAutoLinker` RULES 新增三条规则自动建 `sys_menu_api` 关联：
  - `McpApiKeyController` → GET=mcp:key:list / POST=mcp:key:create / PUT=mcp:key:edit / DELETE=mcp:key:remove
  - `McpServerConfigController` → GET=mcp:server:list / POST=mcp:server:create / PUT=mcp:server:edit / DELETE=mcp:server:remove
  - `McpAuditController` → GET=mcp:audit:view
- 菜单 SQL（mcp-schema.sql）：`3xxx` 段（3000 MCP 管理目录 / 3001 API Key / 3002 外部服务 / 3003 审计日志 + B 型按钮），`sys_role_menu` 授予 ADMIN。

## 七、配置项（application-ai.yml）

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true            # MCP Server 总开关
        name: gj-llm-knowledge
        version: 1.0.0
        protocol: STREAMABLE     # Streamable HTTP 传输
        streamable-http:
          # 端点必须落在 /open/** 下:Security permitAll + 权限拦截器排除,认证完全由 API Key 承担
          # 注意:Spring AI 2.0 的属性是 streamable-http.mcp-endpoint(1.x 的 server.endpoint 已废弃)
          mcp-endpoint: /open/mcp
gj:
  llm:
    mcp:
      server:
        enabled: true            # 平台侧开关（与 starter 开关独立，双闸）
      client:
        request-timeout-ms: 30000      # 外部工具调用超时
        health-check-interval-ms: 60000
        init-timeout-ms: 10000
        enabled: true                  # 全局外部工具总开关（一键止血）
      crypto:
        secret: ""               # 生产必须配置（Base64 32字节）；空=开发默认密钥+WARN
      audit:
        params-max-length: 512
```

chat 侧新增智能体配置（`gj.llm.chat.agents.tool-call`），默认路由不变，实验时切 `default-agent: tool-call`。

## 八、实施顺序

```
1. 底座:   mcp-schema.sql + 实体/Mapper + AES 工具 + API Key 认证链 + 审计事件/监听
2. Server: 检索工具 ×2 + McpServerToolProvider + 拦截器注册 + starter 装配
3. Client: McpConnectionManager + McpToolRegistry + McpHealthChecker
4. Agent:  AbstractLlmAgent 双钩子 + AgentContext 事件通道 + ToolCallAgent + SSE 扩展
5. 管理面: 3 组管理 API + ApiAutoLinker 规则 + 菜单 SQL
6. 前端:   MCP 管理页 ×3 + 对话页工具事件展示
```

### 一期前端落地（gj-llm-web，vite build 验证通过）

| 文件 | 内容 |
|---|---|
| `src/api/modules/mcp.ts` | 三组管理 API（类型注解沿用 system.ts 的 `AxiosResponse<ApiResponse<T>>` 正确范式） |
| `src/views/mcp/ApiKeyManage.vue` | Key 列表 / 发放（完整 Key 一次性弹窗展示+复制）/ 启停 / 吊销 |
| `src/views/mcp/ServerManage.vue` | 外部服务列表 / 新增编辑（凭据掩码保留）/ 连接测试 / 工具清单预览 / 启停 |
| `src/views/mcp/AuditLog.vue` | 双向审计日志分页（方向 / 结果 / 工具名筛选） |
| `src/stores/modules/chat.ts` | `tool_call` / `tool_result` 事件入桶，流式提交时随消息暂存 `tools` |
| `src/components/ChatMessage/ChatMessage.vue` | 工具调用折叠面板（执行中呼吸边框+展开，正文到达收起，含耗时/结果/错误行，暗色适配） |

菜单 component 约定：M 型目录必须带 `system/SystemLayout`（动态路由以它作嵌套布局），mcp-schema.sql 已按此修正。

## 九、已知限制与风险

1. **模型能力依赖**：工具循环要求对话模型支持 function calling（Ollama 下 `gemma2` 不支持，`qwen3`/`llama3.1` 等支持）。ToolCallAgent 为独立智能体，配置工具友好模型后实验；出问题切回 rag-qa 链路，互不影响。
2. **starter 属性名**：Spring AI 2.0 的 `spring.ai.mcp.server.*` 属性若与 1.x 有差异，以构建后生效为准（编译期不可见，首次启动验证）。
3. **API Key 明文仅发放时可见**：设计如此（哈希存储），丢失只能吊销重发。
4. **username 快照**：`mcp_api_key.username` 为发放时快照，用户改名后审计展示沿用快照（接受此权衡，避免跨模块联查）。
