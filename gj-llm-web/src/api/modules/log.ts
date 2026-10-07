import http from '@/api'

/** 后端统一响应信封（com.gj.llm.common.web.R） */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 操作日志 —— 与后端 OperLogEntity 对齐（id 为字符串：雪花 ID 超出 JS 安全整数） */
export interface OperLogItem {
  id: string
  module: string
  type: string
  method: string
  requestUri: string
  requestMethod: string
  operator: string
  userId?: string | null
  ip: string
  params?: string | null
  result?: string | null
  status: number
  errorMsg?: string | null
  costMs: number
  createdAt: string
}

/** 操作日志管理 —— /api/oper-logs（权限点 system:log:*） */
export const operLogApi = {
  /** 分页查询 */
  async page(params: {
    page: number
    size: number
    module?: string
    operator?: string
    status?: number
  }): Promise<{ records: OperLogItem[]; total: number }> {
    const res = await http.get<ApiResult<{ records: OperLogItem[]; total: number }>>('/oper-logs', { params })
    const d = res.data.data
    return { records: d?.records ?? [], total: d?.total ?? 0 }
  },

  /** 清空全部日志 */
  async clearAll(): Promise<void> {
    await http.delete('/oper-logs')
  },
}
