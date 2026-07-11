import http from '@/api'
import type { SendMessageRequest, ChatMessage } from '@/api/types'

export const chatApi = {
  /** 发送消息（SSE 流式），返回 fetch Response 用于读取流 */
  sendMessageStream(data: SendMessageRequest): Promise<Response> {
    const token = localStorage.getItem('ACCESS_TOKEN')
    return fetch(`${import.meta.env.VITE_API_BASE_URL}/v1/chat/send/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(data),
    })
  },

  /** 获取对话历史消息（通过会话 ID） */
  getMessages(conversationId: number | string): Promise<ApiResponse<ChatMessage[]>> {
    return http.get(`/v1/conversations/${conversationId}/messages`)
  },
}
