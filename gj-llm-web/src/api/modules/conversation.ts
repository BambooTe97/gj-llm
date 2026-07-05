import http from '@/api'
import type { Conversation } from '@/api/types'

export const conversationApi = {
  /** 获取会话列表 */
  getList(): Promise<ApiResponse<Conversation[]>> {
    return http.get('/conversations')
  },

  /** 创建新会话 */
  create(title?: string): Promise<ApiResponse<Conversation>> {
    return http.post('/conversations', { title })
  },

  /** 删除会话 */
  remove(id: string): Promise<ApiResponse<null>> {
    return http.delete(`/conversations/${id}`)
  },

  /** 重命名会话 */
  rename(id: string, title: string): Promise<ApiResponse<Conversation>> {
    return http.patch(`/conversations/${id}`, { title })
  },
}
