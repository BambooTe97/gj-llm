import type { App, Directive } from 'vue'
import { useUserStore } from '@/stores/modules/user'

/** v-focus: 元素挂载后自动聚焦 */
function vFocus(el: HTMLElement) {
  el.focus()
}

/**
 * v-permission: 按钮级权限控制。
 *
 * 用法：v-permission="'system:user:add'" 或 v-permission="['system:user:add','system:user:edit']"
 * 当前用户不具备给定权限标识中的任意一个时，元素从 DOM 中移除。
 */
const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const value = binding.value
    const perms = Array.isArray(value) ? value : [value]
    const ok = perms.some((p) => userStore.hasPermission(p))
    if (!ok) {
      el.parentNode?.removeChild(el)
    }
  },
}

export function setupDirectives(app: App) {
  app.directive('focus', { mounted: vFocus })
  app.directive('permission', permission)
}
