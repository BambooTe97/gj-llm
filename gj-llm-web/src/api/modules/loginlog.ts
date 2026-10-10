import http from '@/api'

/** 后端统一响应信封（com.gj.llm.common.web.R） */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 登录日志 —— 与后端 LogininforEntity 对齐（id 为字符串：雪花 ID 超出 JS 安全整数） */
export interface LoginLogItem {
  id: string
  username: string
  userId?: string | null
  ip: string
  browser: string
  os: string
  status: number
  msg: string
  loginTime: string
}

/** 登录日志管理 —— /api/login-logs（权限点 system:loginlog:*） */
export const loginLogApi = {
  /** 分页查询 */
  async page(params: {
    page: number
    size: number
    username?: string
    ip?: string
    status?: number
  }): Promise<{ records: LoginLogItem[]; total: number }> {
    const res = await http.get<ApiResult<{ records: LoginLogItem[]; total: number }>>('/login-logs', { params })
    const d = res.data.data
    return { records: d?.records ?? [], total: d?.total ?? 0 }
  },
}
