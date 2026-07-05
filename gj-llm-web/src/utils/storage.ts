/** localStorage 封装，统一处理 JSON 序列化 */
export const storage = {
  get<T = string>(key: string): T | null {
    try {
      const raw = localStorage.getItem(key)
      if (raw === null) return null
      try {
        return JSON.parse(raw) as T
      } catch {
        return raw as T
      }
    } catch {
      return null
    }
  },

  set(key: string, value: unknown): void {
    const raw = typeof value === 'string' ? value : JSON.stringify(value)
    localStorage.setItem(key, raw)
  },

  remove(key: string): void {
    localStorage.removeItem(key)
  },

  clear(): void {
    localStorage.clear()
  },
}
