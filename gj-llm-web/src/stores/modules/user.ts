import { defineStore } from 'pinia'
import { ref } from 'vue'
import { TOKEN_KEY, USER_INFO_KEY } from '@/constants'
import { storage } from '@/utils/storage'
import { authApi } from '@/api/modules/auth'
import type { LoginRequest } from '@/api/types'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(storage.get(TOKEN_KEY))
  const username = ref<string | null>(null)
  const avatar = ref<string | null>(null)

  /** 登录 */
  async function login(data: LoginRequest): Promise<{ success: boolean; message?: string }> {
    try {
      const res = await authApi.login(data)
      const { token: tk, username: uname, avatar: avt } = res.data
      token.value = tk
      username.value = uname
      avatar.value = avt || null

      storage.set(TOKEN_KEY, tk)
      storage.set(USER_INFO_KEY, { username: uname, avatar: avt })

      return { success: true }
    } catch (error: any) {
      return { success: false, message: error?.message || '登录失败，请稍后重试' }
    }
  }

  /** 登出 */
  async function logout() {
    try {
      await authApi.logout()
    } finally {
      token.value = null
      username.value = null
      avatar.value = null
      storage.remove(TOKEN_KEY)
      storage.remove(USER_INFO_KEY)
      router.push('/login')
    }
  }

  /** 从 storage 恢复用户信息 */
  function restoreUserInfo() {
    const info = storage.get<{ username: string; avatar?: string }>(USER_INFO_KEY)
    if (info) {
      username.value = info.username
      avatar.value = info.avatar || null
    }
  }

  return { token, username, avatar, login, logout, restoreUserInfo }
})
