/** 后端统一响应结构 */
declare interface ApiResponse<T = unknown> {
  code: number
  data: T
  message: string
  /** 提示分级：error=右上角红色通知，warn=黄色轻提示；缺省=未分级（按 error 呈现） */
  level?: 'error' | 'warn'
  /** 业务码（即后端 i18n key），供埋点或特定业务码处理 */
  bizCode?: string
}

/** 分页参数 */
declare interface PageParams {
  page: number
  pageSize: number
}

/** 分页响应 */
declare interface PageResponse<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}
