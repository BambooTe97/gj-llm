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
  createdAt: string
}

/** 发送消息请求 */
export interface SendMessageRequest {
  conversationId?: string
  content: string
  datasetId?: string
  type?: string
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

/** 检索测试结果项 */
export interface SearchResultItem {
  rank: number
  content: string
  score: number
  metadata: Record<string, any>
}

/** MyBatis-Plus IPage 分页响应 */
export interface PageData<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
