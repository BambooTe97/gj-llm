import type { ChatMessage, Conversation } from '@/api/types'

/** App 全局状态 */
export interface AppState {
  sidebarCollapsed: boolean
  theme: 'light' | 'dark'
}

/** 对话状态 */
export interface ChatState {
  messages: ChatMessage[]
  streaming: boolean
  currentAssistantMsg: string
}

/** 会话列表状态 */
export interface ConversationState {
  list: Conversation[]
  currentId: string | null
  loading: boolean
}

/** 用户状态 */
export interface UserState {
  token: string | null
  username: string | null
  avatar: string | null
}
