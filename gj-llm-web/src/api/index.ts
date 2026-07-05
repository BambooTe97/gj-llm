import axios, { AxiosError, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { TOKEN_KEY } from '@/constants'
import { storage } from '@/utils/storage'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
})

// ========== 请求拦截器 ==========
http.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = storage.get<string>(TOKEN_KEY)
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => Promise.reject(error),
)

// ========== 响应拦截器 ==========
http.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { data } = response
    // 如果返回的是 blob 或其他非 JSON，直接返回
    if (!data || typeof data.code === 'undefined') return response

    if (data.code === 401) {
      storage.remove(TOKEN_KEY)
      window.location.href = '/login'
      return Promise.reject(new Error('登录已过期'))
    }

    if (data.code !== 200) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }

    return response
  },
  (error: AxiosError<ApiResponse>) => {
    // 优先取后端返回的 message，其次用 axios 内置错误描述
    const serverMessage = error.response?.data?.message

    if (error.response?.status === 401) {
      storage.remove(TOKEN_KEY)
      window.location.href = '/login'
      ElMessage.error(serverMessage || '登录已过期，请重新登录')
    } else if (error.response?.status === 500) {
      ElMessage.error(serverMessage || '服务器错误')
    } else if (error.message?.includes('timeout')) {
      ElMessage.error('请求超时')
    } else {
      ElMessage.error(serverMessage || error.message || '网络错误')
    }
    // 抛出带后端消息的 Error，让上层调用方可以获取具体错误信息
    return Promise.reject(new Error(serverMessage || error.message || '网络错误'))
  },
)

export default http
