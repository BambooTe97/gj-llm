import http from '@/api'
import type { LoginRequest, LoginResponse } from '@/api/types'

export const authApi = {
  /** 登录 */
  login(data: LoginRequest): Promise<ApiResponse<LoginResponse>> {
    return http.post('/auth/login', data)
  },

  /** 登出 */
  logout(): Promise<ApiResponse<null>> {
    return http.post('/auth/logout')
  },

  /** 刷新 Token */
  refreshToken(): Promise<ApiResponse<LoginResponse>> {
    return http.post('/auth/refresh')
  },
}
