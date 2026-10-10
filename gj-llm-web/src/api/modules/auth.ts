import http from '@/api'
import type { AxiosResponse } from 'axios'
import type { CaptchaResponse, LoginRequest, LoginResponse, UserInfo } from '@/api/types'

/** API 响应类型别名：axios 响应（其 data 为统一 ApiResponse 包装），调用方用 res.data.data 取业务数据 */
type ApiResult<T> = AxiosResponse<ApiResponse<T>>

export const authApi = {
  /** 登录（silent：失败时全局拦截器不弹提示，由登录页在弹窗/表单内展示） */
  login(data: LoginRequest): Promise<ApiResult<LoginResponse>> {
    return http.post('/auth/login', data, { silent: true })
  },

  /** 生成滑动验证码（免认证，登录页调用；开关关闭时返回 enabled:false） */
  generateCaptcha(): Promise<ApiResult<CaptchaResponse>> {
    return http.get('/captcha/generate')
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

  /** 自助修改密码（登录即可调用，无需权限点） */
  changePassword(data: { oldPassword: string; newPassword: string }): Promise<ApiResult<null>> {
    return http.post('/auth/change-password', data)
  },
}
