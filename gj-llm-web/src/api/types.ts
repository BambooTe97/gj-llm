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
}

/** 会话结构 */
export interface Conversation {
  id: string
  title: string
  lastMessage?: string
  updatedAt: string
  createdAt: string
}

/** 文件记录 */
export interface FileRecord {
  id: number
  originalName: string
  extension: string
  size: number
  contentType: string
  createBy: string
  url: string
  createdAt: string
}
