import type { RouteRecordRaw } from 'vue-router'
import type { Menu } from '@/api/types'

// 预加载所有 view 组件，供动态路由按 component 路径匹配。
// key 形如 '../views/system/user/UserManage.vue'
const modules = import.meta.glob('../views/**/*.vue')

/**
 * 将菜单树转换为动态路由配置，由路由守卫挂载到根布局 Layout 下。
 *
 * - 目录(M)/菜单(C) 生成路由；按钮(B) 忽略（仅用于权限标识）
 * - component 路径（如 system/user/UserManage）映射到 ../views/&lt;component&gt;.vue
 * - 目录类型菜单（如系统管理）以其 component（SystemLayout）作为嵌套布局，子菜单作为 children
 */
export function generateRoutes(menus: Menu[]): RouteRecordRaw[] {
  return buildChildren(menus, '')
}

/**
 * 递归构建路由。
 *
 * @param menus      菜单列表
 * @param parentPath 父菜单完整路径（用于把子菜单 path 转为相对父的片段）
 */
function buildChildren(menus: Menu[], parentPath: string): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []
  for (const menu of menus) {
    if (menu.type === 'B') continue // 按钮不生成路由

    const component = resolveComponent(menu.component)
    if (!component) continue // 组件未找到则跳过（避免注册无效路由）

    const fullPath = menu.path || ''
    // 子路径转相对父：去掉父前缀；顶层去掉前导 /
    let relPath: string
    if (parentPath && fullPath.startsWith(parentPath + '/')) {
      relPath = fullPath.slice(parentPath.length + 1)
    } else {
      relPath = fullPath.replace(/^\//, '')
    }

    const childMenus = (menu.children || []).filter((c) => c.type !== 'B')
    const childRoutes = childMenus.length ? buildChildren(childMenus, fullPath) : []

    const route = {
      path: relPath,
      name: `menu_${menu.id}`,
      component,
      meta: { title: menu.name, menuId: menu.id, perms: menu.perms, icon: menu.icon },
      children: childRoutes,
    } as RouteRecordRaw
    routes.push(route)
  }
  return routes
}

/** 按 component 路径解析懒加载组件，找不到则返回 undefined */
function resolveComponent(component?: string | null) {
  if (!component) return undefined
  const path = `../views/${component}.vue`
  return modules[path] as (() => Promise<unknown>) | undefined
}
