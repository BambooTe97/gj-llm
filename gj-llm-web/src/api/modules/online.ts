import http from '@/api'

/** 后端统一响应信封（com.gj.llm.common.web.R） */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 在线用户会话 —— 与后端 OnlineUserVO 对齐（不含 Token；tokenId 为字符串） */
export interface OnlineUser {
  tokenId: string
  userId: string
  username: string
  nickname?: string | null
  ip: string
  browser?: string | null
  os?: string | null
  loginTime: string
}

/** 在线用户管理 —— /api/online/users（权限点 system:online:*） */
export const onlineUserApi = {
  /** 在线会话列表（按登录时间倒序，不分页） */
  async list(): Promise<OnlineUser[]> {
    const res = await http.get<ApiResult<OnlineUser[]>>('/online/users')
    return res.data.data ?? []
  },

  /** 强制下线指定会话 */
  async forceLogout(tokenId: string): Promise<void> {
    await http.delete(`/online/users/${tokenId}`)
  },
}
