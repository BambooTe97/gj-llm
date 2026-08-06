/** 登录请求参数 */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应 */
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  username: string
  nickname: string
  avatar?: string
}

/** 消息结构 */
export interface ChatMessage {
  id: string
  conversationId: string
  role: 'user' | 'assistant' | 'system'
  content: string
  thinking?: string
  createdAt: string
}

/** 发送消息请求 */
export interface SendMessageRequest {
  conversationId?: string
  content: string
  datasetId?: string
  type?: string
  enableThinking?: boolean
}

/** 会话结构 */
export interface Conversation {
  id: string
  title: string
  datasetId?: string
  lastMessage?: string
  messageCount?: number
  updatedAt: string
  createdAt: string
}

/** 文件记录（file_record 表） */
export interface FileRecord {
  id: string
  originalName: string
  extension: string
  size: number
  contentType: string
  createBy: string
  url: string
  createdAt: string
}

/** 知识库 */
export interface Dataset {
  id: string
  name: string
  description: string | null
  embeddingModel: string
  vectorStoreType: string
  collectionName: string
  chunkSize: number
  chunkOverlap: number
  status: string
  docCount: number
  segmentCount: number
  createdAt: string
  updatedAt: string
}

/** 知识库-文件关联记录（含文件信息） */
export interface DatasetFile {
  id: string
  datasetId: string
  fileId: string
  fileName: string
  fileType: string
  fileSize: number
  fileUrl: string
  status: string
  errorMessage: string | null
  segmentCount: number
  progressPercent: number
  currentStep: string | null
  createdAt: string
  updatedAt: string
}

/** 精排测试结果项:同时携带粗排分与精排分,用于对照评估排序 */
export interface RankedTestItem {
  rank: number
  content: string
  coarseScore: number
  rerankScore: number
  source: string | null
  datasetFileId: string | number | null
}

/** 精排测试结果:含 reranker 可用状态 + 精排阈值 + 排序结果 */
export interface TestRankedResult {
  rerankerAvailable: boolean
  rerankScoreThreshold: number
  items: RankedTestItem[]
}

/** 检索评测用例(id/datasetId 新建时缺省) */
export interface EvalQuery {
  id?: string | number
  datasetId?: string | number
  query: string
  expectedSource: string | null
  expectedSnippet: string | null
}

/** 评测结果明细项 */
export interface EvalResultItem {
  query: string
  found: boolean
  rank: number
  topScore: number
  /** 期望文档的精排分(未命中记 0);前端据此做阈值扫描 */
  expectedScore: number
}

/** 检索评测结果:聚合指标 + 每条明细 */
export interface RetrievalEvalResult {
  total: number
  recallAtK: number
  mrr: number
  /** 当前配置的精排采纳阈值,供阈值扫描标记"当前"位置 */
  rerankScoreThreshold: number
  items: EvalResultItem[]
}

/** 检索评测异步任务状态(供前端轮询) */
export interface RetrievalEvalTask {
  taskId: string
  datasetId: string | number
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  total: number
  done: number
  currentStep: string | null
  errorMessage: string | null
  result: RetrievalEvalResult | null
  createdAt: number
}

/** MyBatis-Plus IPage 分页响应 */
export interface PageData<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// ==================== 系统管理 ====================

/** 菜单类型：M=目录, C=菜单, B=按钮 */
export type MenuType = 'M' | 'C' | 'B'

/** 菜单 */
export interface Menu {
  id: number
  parentId: number
  name: string
  type: MenuType
  path?: string | null
  component?: string | null
  perms?: string | null
  icon?: string | null
  sort: number
  visible: number
  status: number
  createBy?: string | null
  createdAt?: string
  updatedAt?: string
  children?: Menu[]
}

/** 角色 */
export interface Role {
  id: number
  name: string
  code: string
  description?: string | null
  createdAt?: string
}

/** 系统用户（管理用，不含密码） */
export interface SysUser {
  id: number
  username: string
  nickname?: string | null
  avatar?: string | null
  email?: string | null
  status: number
  createdAt?: string
  updatedAt?: string
  roles?: Role[]
}

/** 创建/更新用户请求体 */
export interface SysUserWrite {
  username?: string
  password?: string
  nickname?: string
  email?: string
  status?: number
  roleIds?: number[]
}

/** 创建/更新菜单请求体 */
export interface MenuWrite {
  parentId?: number
  name?: string
  type?: MenuType
  path?: string
  component?: string
  perms?: string
  icon?: string
  sort?: number
  visible?: number
  status?: number
}

/** 当前登录用户信息（/api/auth/userinfo 返回） */
export interface UserInfo {
  id: number
  username: string
  nickname?: string | null
  avatar?: string | null
  roles: string[]
  permissions: string[]
  menus: Menu[]
}

/** 系统接口（sys_api，自动扫描入库） */
export interface ApiItem {
  id: number
  controller: string
  methodName: string
  httpMethod: string
  path: string
  summary?: string | null
}
