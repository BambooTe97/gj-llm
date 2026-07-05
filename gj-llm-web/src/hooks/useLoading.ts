import { ref } from 'vue'

/**
 * 通用的 loading 状态管理
 * @param initialState 初始 loading 值
 */
export function useLoading(initialState = false) {
  const loading = ref(initialState)

  function start() {
    loading.value = true
  }

  function stop() {
    loading.value = false
  }

  /** 包裹异步函数，自动管理 loading 状态 */
  async function wrap<T>(fn: () => Promise<T>): Promise<T | undefined> {
    loading.value = true
    try {
      return await fn()
    } finally {
      loading.value = false
    }
  }

  return { loading, start, stop, wrap }
}
