import type { App } from 'vue'

/** v-focus: 元素挂载后自动聚焦 */
function vFocus(el: HTMLElement) {
  el.focus()
}

export function setupDirectives(app: App) {
  app.directive('focus', {
    mounted: vFocus,
  })
}
