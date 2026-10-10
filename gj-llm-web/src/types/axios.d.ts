import 'axios'

/** 请求配置扩展：静默模式下失败不弹全局提示（如登录页在弹窗内自行展示错误） */
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}
