import http from '@/api'

/** 后端统一响应信封（com.gj.llm.common.web.R） */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 通知条目 —— 与后端 NotifyVO 对齐（id 为字符串：雪花 ID 超出 JS 安全整数） */
export interface NotifyItem {
  id: string
  title: string
  content: string
  level: 'info' | 'success' | 'warning' | 'error'
  readFlag: 0 | 1
  createdAt: string
}

export const notifyApi = {
  /** 当前用户的通知列表（最近 50 条） */
  async list(): Promise<NotifyItem[]> {
    const res = await http.get<ApiResult<NotifyItem[]>>('/v1/notify')
    return res.data.data ?? []
  },

  /** 当前用户的未读数 */
  async unreadCount(): Promise<number> {
    const res = await http.get<ApiResult<number>>('/v1/notify/unread-count')
    return Number(res.data.data ?? 0)
  },

  /** 标记单条已读 */
  async markRead(id: number): Promise<void> {
    await http.post(`/v1/notify/${id}/read`)
  },

  /** 全部标记已读 */
  async markAllRead(): Promise<void> {
    await http.post('/v1/notify/read-all')
  },

  /** 发送测试通知（发给当前用户；WS 在线实时推送，离线落库） */
  async sendTest(payload?: { title?: string; content?: string; level?: string }): Promise<NotifyItem | null> {
    const res = await http.post<ApiResult<NotifyItem>>('/v1/notify/send-test', payload ?? {})
    return res.data.data ?? null
  },
}

/** 管理端通知条目 —— 与后端 NotifyAdminVO 对齐（含接收人用户名解析） */
export interface NotifyAdminItem {
  id: string
  userId: number
  username: string
  nickname: string | null
  title: string
  content: string
  level: 'info' | 'success' | 'warning' | 'error'
  readFlag: 0 | 1
  createdAt: string
  readAt: string | null
}

/** 通知管理中心 —— /api/v1/notify/admin（权限点 notify:manage） */
export const notifyAdminApi = {
  /** 全量通知分页 */
  async page(params: {
    page: number
    size: number
    keyword?: string
    level?: string
    readFlag?: number
  }): Promise<{ records: NotifyAdminItem[]; total: number }> {
    const res = await http.get<ApiResult<{ records: NotifyAdminItem[]; total: number }>>('/v1/notify/admin', {
      params,
    })
    const d = res.data.data
    return { records: d?.records ?? [], total: d?.total ?? 0 }
  },

  /** 定向发送通知给指定用户 */
  async send(payload: { userId: string; title: string; content: string; level: string }): Promise<void> {
    await http.post('/v1/notify/admin/send', payload)
  },

  /** 删除通知 */
  async remove(id: string): Promise<void> {
    await http.delete(`/v1/notify/admin/${id}`)
  },
}
