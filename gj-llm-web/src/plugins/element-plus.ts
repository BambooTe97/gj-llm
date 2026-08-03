import type { App } from 'vue'
// Element Plus 组件通过 unplugin-vue-components 按需导入
// 此处仅注册图标和全局配置

// 命令式调用组件（ElMessage / ElMessageBox 等）不会在模板里以标签形式出现，
// unplugin-vue-components 不会自动加载它们的样式，需手动引入，否则会样式缺失
// （例如 ElMessageBox 确认框会丢失 .el-overlay-message-box 的居中定位，贴在视口顶部）
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

export function setupElementPlus(app: App) {
  // 如需全局配置 Element Plus，可在此设置
  // app.config.globalProperties.$ELEMENT = { size: 'default' }
  void app // 暂时保留 app 引用，后续按需使用
}
