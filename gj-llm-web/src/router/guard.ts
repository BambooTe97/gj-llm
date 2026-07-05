import type { Router } from 'vue-router'
import { TOKEN_KEY } from '@/constants'
import { storage } from '@/utils/storage'

const WHITE_LIST = ['/login', '/404']

export function setupRouterGuard(router: Router) {
  router.beforeEach((to, _from, next) => {
    // 动态设置页面标题
    document.title = (to.meta.title as string) || import.meta.env.VITE_APP_TITLE

    const token = storage.get<string>(TOKEN_KEY)

    if (token) {
      // 已登录，访问登录页则重定向到首页
      if (to.path === '/login') {
        next('/chat')
        return
      }
      next()
    } else {
      // 未登录，白名单放行，其余跳转登录
      if (WHITE_LIST.includes(to.path) || to.meta.noAuth) {
        next()
      } else {
        next(`/login?redirect=${to.path}`)
      }
    }
  })
}
