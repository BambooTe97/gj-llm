# gj-llm 权限系统说明（菜单-按钮-接口权限模型）

> 面向第一次接触本系统的人：读一遍就能明白权限是怎么配置、怎么生效、怎么扩展的。
> RBAC 中心代码在 `gj-base-admin`，本文所有类名均可按图索骥。

---

## 一、一句话总览

**菜单表里配置"谁能看到什么"（页面/按钮），接口表里配置"哪个按钮对应哪些后端接口"；用户登录后前端按菜单显隐界面，后端按"按钮↔接口"关联拦截请求，两级都在，缺一不可。**

```
用户 ──登录──> 角色 ──分配──> 菜单/按钮(perms 标识)
                                   │
                 前端:显隐界面 ←───┤(userinfo 下发 menus + permissions)
                                   │
                 后端:拦截请求 ←───┘(sys_menu_api:按钮 perms ↔ sys_api 接口)
```

---

## 二、核心概念与数据模型

| 表 | 作用 | 关键列 |
|---|---|---|
| `sys_menu` | 菜单树，三层类型 | `type`: **M**=目录 / **C**=菜单(页面) / **B**=按钮(权限点)；`perms` 权限标识；`component` 前端组件路径 |
| `sys_role` / `sys_role_menu` | 角色及角色-菜单分配 | 角色 = 一组菜单(含按钮)的集合 |
| `sys_api` | 系统全部后端接口（**启动时自动扫描，无需手工维护**） | controller / http_method / path(含 `{id}` 占位符) |
| `sys_menu_api` | 按钮(B) ↔ 接口 的关联 | 一个按钮可关联多个接口 |

### 权限标识（perms）命名规范

```
<模块>:<资源>:<动作>        如 system:user:add、mcp:key:create
<模块>:<子资源>:<动作>      内页纵深功能加子资源段，如 dataset:doc:upload
```

B 型按钮的 `perms` 就是权限标识本身；M/C 型一般无 perms（页面进入权限由其后端接口的 perms 隐式决定）。

### 菜单 ID 分段约定

`1xxx`=业务菜单(聊天/知识库) `2xxx`=系统管理 `3xxx`=MCP管理。按钮权限点挂在所属 C 菜单下，ID 顺延（如知识库 1001 下 1101-1108）。

---

## 三、运行流程

### 3.1 登录后（前端拿到什么）

1. 登录成功 → 前端请求 `/api/auth/userinfo`，返回：菜单树 `menus` + 权限标识数组 `permissions`（= 该用户所有角色分配的菜单/按钮的 perms 并集）。
2. **动态路由注册**（`router/dynamic.ts`）：遍历菜单树，M/C 型生成路由——`component` 字段（如 `mcp/ApiKeyManage`）映射到 `src/views/<component>.vue`；**B 型按钮跳过**（只作权限标识）。目录(M)必须配 `system/SystemLayout` 组件作为嵌套布局。
3. **界面显隐**：
   - 普通按钮：`v-permission="'xxx:yyy:zzz'"` 指令（无权限直接从 DOM 移除）；
   - `el-tab-pane` 等组件型显隐：必须用 `v-if="userStore.hasPermission(...)"`（指令的 mounted 移除 DOM 无法同步 tabs 的页签头，要让组件根本不创建）。

### 3.2 请求时（后端怎么拦）

`ApiPermissionInterceptor`（gj-base-admin）对每个请求：

```
白名单(/open/**、login/refresh/userinfo/logout) ──放行
非 Controller(静态资源) ──放行
ADMIN 角色 ──全放行(硬编码)
按 method+path 匹配 sys_api：
  未扫描入库的接口 ──放行
  接口无任何关联权限点 ──拒绝(默认拒绝,403 "该接口未开放访问权限")
  用户 permissions 含接口任一权限点 ──放行，否则 403 "权限不足"
```

**安全语义是"默认拒绝"**：一个接口只要没配置权限点，非 admin 就是不可用——漏配的代价是功能坏了，不是越权。

### 3.3 缓存与生效时机

用户权限快照（角色 + permissions）在鉴权时聚合一次，缓存在 **Redis（key `login:user:{用户名}`，TTL 30 分钟）**。失效由**应用层事件**驱动（`SecurityUserServiceImpl` 的事件监听）：用户变更清单人、角色分配/菜单/接口关联变更清全部。

- **通过系统管理页面**（角色管理/菜单管理）改动 → 事件自动失效缓存，下一次请求即生效（前端界面刷新页面重新拉 userinfo 即可）。
- **直接执行 SQL 改 sys_menu / sys_role_menu → 不触发任何事件**，缓存要等 30 分钟 TTL 过期。想立即生效：删 Redis key 后刷新页面，如 `redis-cli DEL login:user:admin`。
- 注意：**"重新登录"不能绕过这份缓存**——登录鉴权同样先读 Redis 缓存，所以直接改库后光重新登录没用。

---

## 四、自动装配机制（gj-base-admin/init/）

### ApiScanner（ApplicationRunner）
启动时扫描全部 Controller 的 RequestMapping 入库 `sys_api`，带 `{var}` 占位符的完整模式值（如 `/api/v1/datasets/{datasetId}/documents/upload`）；排除 `/open`、`/error`。接口改动后重启自动同步，删掉的接口标 `is_deleted`。

### ApiAutoLinker（ApplicationRunner，在 Scanner 之后）
按两级规则为接口自动建立"按钮↔接口"关联（`sys_menu_api`）：

1. **精确路径规则 `PATH_RULES`**（优先）：AntPathMatcher 模式匹配，用于页面纵深功能的独立权限点。例：`POST /api/v1/datasets/*/documents/upload → dataset:doc:upload`。
2. **方法粗映射 `RULES`**（兜底）：Controller 简单名 + HTTP 方法 → perms。例：DatasetController 的 GET/POST/PUT/DELETE → dataset:view/create/edit/delete。
3. 只**新增**不存在的关联，**不覆盖** admin 在菜单管理页手工做的调整；规则没覆盖的 Controller（如 FileController、EvalController）不自动关联。

---

## 五、如何扩展（加页面/加菜单/加按钮功能）

**两条路径都能做，场景不同：日常微调用页面，新功能交付用种子 SQL。**

### 路径 A：页面操作（系统管理 → 菜单管理）

页面完全支持，无需写 SQL：

| 想做什么 | 在菜单管理页怎么做 |
|---|---|
| 加一个目录(M)/页面(C) | 新增菜单：填名称、类型、路由 path、**component**（必须与 `src/views/` 下的文件路径一致，如 `mcp/ApiKeyManage`；M 目录固定填 `system/SystemLayout`）、图标、排序 |
| 加一个按钮权限点(B) | 在目标页面菜单下新增"按钮"类型子项，填**权限标识**（perms）与排序 |
| 调整菜单顺序/显隐 | 编辑对应菜单的排序 / visible 字段（本次"系统管理与MCP管理对调"即可在此操作） |
| 按钮关联接口 | 编辑按钮 → 分配接口（从 sys_api 列表勾选；也可什么都不做，等 ApiAutoLinker 自动关联） |
| 给角色授权 | 系统管理 → 角色管理 → 分配菜单（勾到按钮级） |

**生效条件：重新登录**（前端路由/权限快照在登录时建立）。

### 路径 B：种子 SQL（新功能随代码交付）

参考 `sql/gj-base/auth-schema.sql`（文末"知识库详情页功能权限点细化"段）、`sql/gj-llm-admin/mcp-schema.sql`：

```sql
-- 1) 菜单/按钮行(INSERT IGNORE 幂等)
INSERT IGNORE INTO sys_menu (...) VALUES (1104, 1001, '上传文档', 'B', 'dataset:doc:upload', ...);
-- 2) 角色授权
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu WHERE ...;
```

好处：脚本幂等可重跑，所有环境（他人库/测试/生产）执行同一脚本即对齐，不依赖有人记得在页面上点。

### "四件套"约定（新增任何功能权限的 checklist）

| # | 件 | 缺了会怎样 |
|---|---|---|
| 1 | perms 字符串（命名规范） | 无标识可配 |
| 2 | `sys_menu` B 型按钮行 | 角色无法勾选该权限 |
| 3 | `ApiAutoLinker` 规则（PATH_RULES 精确路径 或 RULES 粗映射） | 接口无权限点 → 非 admin 全部 403，功能不可用 |
| 4 | 前端 `v-permission` 或 `v-if="hasPermission(...)"` | 无权限用户看得到按钮、点了 403（体验差，但不越权） |

**实例对照**：
- 页面级 CRUD：`mcp:key:create`（MCP 管理页，RULES 粗映射）；
- 内页纵深功能：`dataset:doc:upload`（知识库详情页上传，PATH_RULES 精确路径 + 详情页 v-if 显隐）。

---

## 六、当前局限性与注意事项

1. **菜单驱动模型的边界**：导航页（M/C）天然被覆盖；**页面纵深功能必须显式建点**。没建点的纵深功能对非 admin 是"不可用"（默认拒绝），安全上不裸奔，但功能上不可用——所以新增功能必须走四件套。
2. **功能权限 ≠ 数据权限**：本模型回答"能不能用上传功能"；"能不能碰这个知识库"由库级可见域（`resource_acl` + `DatasetVisibleService`，fail-closed）回答。两层正交，请求要同时通过。检索链路还有第二道可见域交集校验。
3. **详情页等静态路由不做进入校验**：`/datasets/:id` 这类详情页是前端静态路由，任何登录用户可直达 URL；数据安全由后端接口权限 + 可见域兜底，页面内按钮按权限隐藏。
4. **ADMIN 全放行是代码级硬编码**（`ApiPermissionInterceptor` 第 4 步），不查库。
5. **未扫描入库的接口直接放行**：ApiScanner 排除了 `/open`（该前缀由 MCP 的 API Key 拦截器认证）和 `/error`；若未来新增白名单外的排除规则需评估暴露面。
6. **权限快照不实时**：快照缓存于 Redis（key `login:user:{用户名}`，TTL 30 分钟），仅由应用层事件失效。页面操作（角色/菜单管理）自动失效；**直接执行 SQL 改权限表不走事件**，需手动清对应 Redis key 或等 TTL 过期——重新登录无效。
7. **前端显隐是体验层，不是安全边界**：v-permission/v-if 只是隐藏按钮，绕过前端直接调接口仍会被后端拦截器拦住。永远以后端为准。
8. **检索评测功能暂无权限点**：EvalController 未配置规则 → 非 admin 默认拒绝，前端暂按 ADMIN 角色隐藏该 tab（补权限点后改为 hasPermission）。
9. **组件型显隐必须用 v-if**：`el-tab-pane`、`el-popconfirm` 等由父组件接管的元素，v-permission 指令的 mounted 移除不可靠。

---

## 七、快速排查

| 症状 | 优先检查 |
|---|---|
| 接口 403 "该接口未开放访问权限" | 接口没关联任何权限点：补 ApiAutoLinker 规则或在菜单管理页给按钮分配该接口 |
| 接口 403 "权限不足" | 角色没勾对应按钮权限，或改完没重新登录 |
| 按钮不显示 | perms 拼写不一致 / 角色未授权 / 用在了组件型元素上（换 v-if） |
| 页面 404 | M/C 菜单的 component 为空、拼错，或不匹配 `src/views/` 下实际文件（M 目录还须是 `system/SystemLayout`） |
| 菜单改了不生效 | 页面操作：刷新页面即可（事件已自动清缓存）；**直接执行的 SQL：删 Redis key `login:user:{用户名}` 后刷新**（重新登录无效），并确认 sys_role_menu 里角色授权行已插入 |
