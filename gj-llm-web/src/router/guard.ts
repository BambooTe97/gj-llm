import type { Router } from 'vue-router'
import { TOKEN_KEY, HOME_PATH } from '@/constants'
import { storage } from '@/utils/storage'
import { useUserStore } from '@/stores/modules/user'
import { generateRoutes } from './dynamic'

const WHITE_LIST = ['/login', '/404']

export function setupRouterGuard(router: Router) {
  router.beforeEach(async (to, _from, next) => {
    // 动态设置页面标题
    document.title = (to.meta.title as string) || import.meta.env.VITE_APP_TITLE

    const token = storage.get<string>(TOKEN_KEY)
    const userStore = useUserStore()

    if (token) {
      // 已登录访问登录页 -> 跳首页
      if (to.path === '/login') {
        next(HOME_PATH)
        return
      }

      // 用户信息未加载 -> 拉取菜单/权限并注册动态路由后重放导航
      if (!userStore.loaded) {
        try {
          await userStore.fetchUserInfo()
          const dynamicRoutes = generateRoutes(userStore.menus)
          dynamicRoutes.forEach((r) => router.addRoute('Layout', r))
          // 刷新时目标路径可能已被通配规则重定向到 /404，需取回原始路径重放，
          // 否则会停在 /404；动态路由此时已注册，重放即可正确匹配
          const target = to.redirectedFrom ?? to
          if (target.path === '/') {
            // 根路径无默认子路由，登录态下跳首页
            next({ path: HOME_PATH, replace: true })
          } else {
            next({ path: target.path, query: target.query, hash: target.hash, replace: true })
          }
          return
        } catch {
          // 拉取失败（Token 失效等）-> 清状态跳登录；redirect 取原始路径而非 /404
          userStore.resetState()
          storage.remove(TOKEN_KEY)
          next(`/login?redirect=${to.redirectedFrom?.path ?? to.path}`)
          return
        }
      }

      // 已加载：访问根路径时跳首页
      if (to.path === '/') {
        next({ path: HOME_PATH, replace: true })
        return
      }
      next()
    } else {
      // 未登录：白名单放行，其余跳登录
      if (WHITE_LIST.includes(to.path) || to.meta.noAuth) {
        next()
      } else {
        next(`/login?redirect=${to.path}`)
      }
    }
  })
}
