import http from '@/api'
import type { Conversation } from '@/api/types'

export const conversationApi = {
  /** 获取会话列表 */
  getList(): Promise<ApiResponse<Conversation[]>> {
    return http.get('/v1/conversations')
  },

  /** 创建新会话 */
  create(title?: string, datasetId?: string): Promise<ApiResponse<Conversation>> {
    return http.post('/v1/conversations', { title, datasetId })
  },

  /** 删除会话 */
  remove(id: string): Promise<ApiResponse<null>> {
    return http.delete(`/v1/conversations/${id}`)
  },

  /** 重命名会话 */
  rename(id: string, title: string): Promise<ApiResponse<Conversation>> {
    return http.patch(`/v1/conversations/${id}`, { title })
  },
}
