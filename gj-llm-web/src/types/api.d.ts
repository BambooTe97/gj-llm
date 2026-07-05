/** 后端统一响应结构 */
declare interface ApiResponse<T = unknown> {
  code: number
  data: T
  message: string
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
