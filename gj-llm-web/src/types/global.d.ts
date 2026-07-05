/** 环境变量类型声明 */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_APP_TITLE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/** 通用对象 */
declare type Recordable<T = unknown> = Record<string, T>
