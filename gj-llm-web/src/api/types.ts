/** 登录请求参数 */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应 */
export interface LoginResponse {
  token: string
  username: string
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
