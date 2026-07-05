import type { App } from 'vue'
// Element Plus 组件通过 unplugin-vue-components 按需导入
// 此处仅注册图标和全局配置

export function setupElementPlus(app: App) {
  // 如需全局配置 Element Plus，可在此设置
  // app.config.globalProperties.$ELEMENT = { size: 'default' }
  void app // 暂时保留 app 引用，后续按需使用
}
