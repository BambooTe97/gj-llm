import axios, { AxiosError, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage, ElNotification } from 'element-plus'
import { REFRESH_TOKEN_KEY, TOKEN_KEY } from '@/constants'
import { storage } from '@/utils/storage'

/**
 * 统一错误提示分级：error 级（及未分级）走右上角红色通知，
 * warn 级走黄色轻提示 —— 与后端 BusinessException 分级契约对齐。
 */
function notify(level: ApiResponse['level'], message: string) {
  if (level === 'warn') {
    ElMessage.warning(message)
  } else {
    ElNotification({ type: 'error', title: '操作失败', message, duration: 5000 })
  }
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30_000,
})

/** 清空本地双 Token */
function clearTokens() {
  storage.remove(TOKEN_KEY)
  storage.remove(REFRESH_TOKEN_KEY)
}

/** 跳转到登录页，携带当前页面路径以便登录后返回 */
function redirectToLogin() {
  // 避免在登录页重复跳转
  if (window.location.pathname === '/login') return

  clearTokens()
  const redirect = encodeURIComponent(window.location.pathname + window.location.search)
  window.location.href = `/login?redirect=${redirect}`
}

// ========== Token 静默刷新（单飞 + 挂起重放） ==========

interface PendingRequest {
  resolve: (token: string) => void
  reject: (err: unknown) => void
}

let isRefreshing = false
/** 刷新期间到达的 401 请求队列，刷新完成后统一重放 */
let failedQueue: PendingRequest[] = []

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach(({ resolve, reject }) => (error ? reject(error) : resolve(token!)))
  failedQueue = []
}

/**
 * 调用 /auth/refresh 换新 Access Token。
 *
 * <p>后端从 Authorization 头取 Refresh Token（Bearer），与业务请求同形；
 * 用独立 axios 实例直连，避免撞到本文件拦截器造成递归刷新。</p>
 */
async function requestNewAccessToken(): Promise<string> {
  const refreshToken = storage.get<string>(REFRESH_TOKEN_KEY)
  if (!refreshToken) {
    throw new Error('缺少刷新令牌')
  }
  const res = await axios.post<ApiResponse<{ accessToken?: string }>>(
    `${import.meta.env.VITE_API_BASE_URL}/auth/refresh`,
    null,
    { headers: { Authorization: `Bearer ${refreshToken}` }, timeout: 10_000 },
  )
  const newToken = res.data?.data?.accessToken
  if (res.data?.code !== 200 || !newToken) {
    throw new Error(res.data?.message || '刷新失败')
  }
  return newToken
}

/**
 * 统一 401 处理：单飞刷新 + 队列重放。
 *
 * <p>刷新成功后更新本地 token 并重放原请求；刷新失败（refresh token 也失效）
 * 清空令牌跳登录页。已重放过仍 401 的请求（_retry 标记）与刷新请求自身
 * 不再进入刷新流程，防止死循环。</p>
 */
async function handle401(originalConfig?: InternalAxiosRequestConfig): Promise<AxiosResponse> {
  const original = originalConfig as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
  if (!original || original._retry || original.url?.includes('/auth/refresh')) {
    clearTokens()
    redirectToLogin()
    return Promise.reject(new Error('登录已过期'))
  }

  if (isRefreshing) {
    // 刷新进行中：挂起本请求，等新 token 发放后重放
    return new Promise<string>((resolve, reject) => {
      failedQueue.push({ resolve, reject })
    }).then((token) => {
      original.headers.Authorization = `Bearer ${token}`
      return http(original)
    })
  }

  original._retry = true
  isRefreshing = true
  try {
    const newToken = await requestNewAccessToken()
    storage.set(TOKEN_KEY, newToken)
    processQueue(null, newToken)
    original.headers.Authorization = `Bearer ${newToken}`
    return http(original)
  } catch (refreshErr) {
    // 刷新失败：所有挂起请求一并失败，跳登录
    processQueue(refreshErr, null)
    clearTokens()
    redirectToLogin()
    return Promise.reject(refreshErr instanceof Error ? refreshErr : new Error('登录已过期'))
  } finally {
    isRefreshing = false
  }
}

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
  async (response: AxiosResponse<ApiResponse>) => {
    const { data } = response
    // 如果返回的是 blob 或其他非 JSON，直接返回
    if (!data || typeof data.code === 'undefined') return response

    if (data.code === 401) {
      // 业务层 401（HTTP 200 包装）同样走静默刷新
      return handle401(response.config)
    }

    if (data.code !== 200) {
      // silent 请求（如登录）由页面在弹窗/表单内自行展示错误，不重复弹全局提示
      if (!response.config.silent) notify(data.level, data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }

    return response
  },
  async (error: AxiosError<ApiResponse>) => {
    // 优先取后端返回的 message，其次用 axios 内置错误描述
    const serverMessage = error.response?.data?.message

    if (error.response?.status === 401) {
      try {
        return await handle401(error.config ?? undefined)
      } catch (err) {
        if (window.location.pathname !== '/login') {
          ElMessage.error(serverMessage || '登录已过期，请重新登录')
        }
        return Promise.reject(err instanceof Error ? err : new Error(serverMessage || '登录已过期'))
      }
    }
    // HTTP 层错误（500/超时/断网）一律按 error 级呈现；silent 请求由调用方自行展示
    const silent = error.config?.silent ?? false
    if (error.response?.status === 500) {
      if (!silent) notify('error', serverMessage || '服务器错误')
    } else if (error.message?.includes('timeout')) {
      if (!silent) notify('error', '请求超时')
    } else {
      if (!silent) notify('error', serverMessage || error.message || '网络错误')
    }
    // 抛出带后端消息的 Error，让上层调用方可以获取具体错误信息
    return Promise.reject(new Error(serverMessage || error.message || '网络错误'))
  },
)

export default http
