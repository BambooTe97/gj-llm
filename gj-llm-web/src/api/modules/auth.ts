import http from '@/api'
import type { AxiosResponse } from 'axios'
import type { LoginRequest, LoginResponse, UserInfo } from '@/api/types'

/** API 响应类型别名：axios 响应（其 data 为统一 ApiResponse 包装），调用方用 res.data.data 取业务数据 */
type ApiResult<T> = AxiosResponse<ApiResponse<T>>

export const authApi = {
  /** 登录 */
  login(data: LoginRequest): Promise<ApiResult<LoginResponse>> {
    return http.post('/auth/login', data)
  },

  /** 登出 */
  logout(): Promise<ApiResult<null>> {
    return http.post('/auth/logout')
  },

  /** 刷新 Token */
  refreshToken(): Promise<ApiResult<LoginResponse>> {
    return http.post('/auth/refresh')
  },

  /** 获取当前登录用户信息（含角色、权限、菜单树） */
  getUserInfo(): Promise<ApiResult<UserInfo>> {
    return http.get('/auth/userinfo')
  },
}
