import { defineStore } from 'pinia'
import { ref } from 'vue'
import { TOKEN_KEY, USER_INFO_KEY } from '@/constants'
import { storage } from '@/utils/storage'
import { authApi } from '@/api/modules/auth'
import type { LoginRequest, Menu } from '@/api/types'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(storage.get(TOKEN_KEY))
  const username = ref<string | null>(null)
  const nickname = ref<string | null>(null)
  const avatar = ref<string | null>(null)
  /** 角色编码列表（如 ADMIN、USER） */
  const roles = ref<string[]>([])
  /** 权限标识列表（如 system:user:list） */
  const permissions = ref<string[]>([])
  /** 当前用户可访问的菜单树（仅目录/菜单类型） */
  const menus = ref<Menu[]>([])
  /** 用户信息是否已加载（避免路由守卫重复拉取） */
  const loaded = ref(false)

  /**
   * 拉取当前登录用户信息（角色/权限/菜单树）。
   *
   * <p>登录后及页面刷新时由路由守卫调用，据此动态注册路由、渲染导航。
   * 失败时抛出异常，由调用方处理（通常清 Token 跳登录）。</p>
   */
  async function fetchUserInfo(): Promise<void> {
    const res = await authApi.getUserInfo()
    const info = res.data.data
    username.value = info.username
    nickname.value = info.nickname || null
    avatar.value = info.avatar || null
    roles.value = info.roles || []
    permissions.value = info.permissions || []
    menus.value = info.menus || []
    storage.set(USER_INFO_KEY, { username: info.username, avatar: info.avatar })
    loaded.value = true
  }

  /** 判断当前用户是否拥有某权限标识 */
  function hasPermission(perm: string): boolean {
    return permissions.value.includes(perm)
  }

  /** 判断是否拥有给定角色编码之一 */
  function hasRole(...codes: string[]): boolean {
    return codes.some((c) => roles.value.includes(c))
  }

  /** 登录 */
  async function login(data: LoginRequest): Promise<{ success: boolean; message?: string }> {
    try {
      const res = await authApi.login(data)
      // res.data 是 ApiResponse 包装层，内层 data 才是 LoginResponse
      const { accessToken: tk, username: uname, avatar: avt } = res.data.data
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
      resetState()
      storage.remove(TOKEN_KEY)
      storage.remove(USER_INFO_KEY)
      router.push('/login')
    }
  }

  /** 重置状态（登出/Token 失效时调用） */
  function resetState() {
    token.value = null
    username.value = null
    nickname.value = null
    avatar.value = null
    roles.value = []
    permissions.value = []
    menus.value = []
    loaded.value = false
  }

  /** 从 storage 恢复基础用户信息（菜单/权限需 fetchUserInfo 拉取） */
  function restoreUserInfo() {
    const info = storage.get<{ username: string; avatar?: string }>(USER_INFO_KEY)
    if (info) {
      username.value = info.username
      avatar.value = info.avatar || null
    }
  }

  return {
    token, username, nickname, avatar, roles, permissions, menus, loaded,
    login, logout, fetchUserInfo, hasPermission, hasRole, restoreUserInfo, resetState,
  }
})
