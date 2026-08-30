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

/** 引用片段（RAG 检索命中的参考来源，assistant 消息携带） */
export interface ChatReference {
  /** 编号（从 1 开始，与回答正文中的行内角标 [n] 对应） */
  rank: number
  /** 片段正文（父块内容，截断 200 字，与模型所见同源） */
  content: string
  /** rerank 精排分数 */
  score: number
  /** 来源文件名 */
  source: string | null
  /** 所属知识库名称（多知识库检索时逐条区分） */
  datasetName: string | null
  /** 所属知识库 ID */
  datasetId: string | number | null
  /** 知识库-文件关联 ID */
  datasetFileId: string | number | null
}

/** 消息结构 */
export interface ChatMessage {
  id: string
  conversationId: string
  role: 'user' | 'assistant' | 'system'
  content: string
  thinking?: string
  /** RAG 检索引用片段（流式 done 后挂载；历史消息由 metadata 还原） */
  references?: ChatReference[]
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
  /** rerank 精排采纳阈值(默认0.3,评测后可采纳推荐值) */
  rerankScoreThreshold: number
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
  /** 关联用例 ID(前端据此将结果合入用例行) */
  queryId: string | number
  query: string
  found: boolean
  rank: number
  topScore: number
  /** 期望文档的精排分(未命中记 0);前端据此做阈值扫描 */
  expectedScore: number
  /** top-K 候选明细(行展开钻取) */
  candidates: CandidateDetail[]
}

/** 候选文档明细(钻取展示) */
export interface CandidateDetail {
  text: string
  score: number
  source: string | null
  expected: boolean
}

/** 检索评测结果:聚合指标 + 每条明细 */
export interface RetrievalEvalResult {
  total: number
  recallAtK: number
  mrr: number
  /** 当前配置的精排采纳阈值,供阈值扫描标记"当前"位置 */
  rerankScoreThreshold: number
  /** 系统计算的推荐阈值(有效召回率≥95%·Recall@5 的最高阈值) */
  recommendedThreshold: number
  /** 推荐理由 */
  recommendationReason: string
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
