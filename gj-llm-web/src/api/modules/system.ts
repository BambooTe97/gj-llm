import http from '@/api'
import type { AxiosResponse } from 'axios'
import type { ApiItem, Menu, MenuWrite, PageData, Role, SysUser, SysUserWrite } from '@/api/types'

/** API 响应类型别名：axios 响应（其 data 为统一 ApiResponse 包装），调用方用 res.data.data 取业务数据 */
type ApiResult<T> = AxiosResponse<ApiResponse<T>>

/** 用户管理 API */
export const userApi = {
  /** 分页查询用户（支持用户名/昵称模糊搜索） */
  getList(page = 1, size = 10, keyword?: string): Promise<ApiResult<PageData<SysUser>>> {
    return http.get('/users', { params: { page, size, keyword } })
  },

  /** 用户详情 */
  getById(id: number): Promise<ApiResult<SysUser>> {
    return http.get(`/users/${id}`)
  },

  /** 创建用户 */
  create(data: SysUserWrite): Promise<ApiResult<SysUser>> {
    return http.post('/users', data)
  },

  /** 更新用户 */
  update(id: number, data: SysUserWrite): Promise<ApiResult<SysUser>> {
    return http.put(`/users/${id}`, data)
  },

  /** 重置密码 */
  resetPassword(id: number, newPassword: string): Promise<ApiResult<null>> {
    return http.put(`/users/${id}/password`, { newPassword })
  },

  /** 删除用户 */
  delete(id: number): Promise<ApiResult<null>> {
    return http.delete(`/users/${id}`)
  },
}

/** 角色管理 API */
export const roleApi = {
  /** 角色列表 */
  getList(): Promise<ApiResult<Role[]>> {
    return http.get('/roles')
  },

  /** 创建角色 */
  create(data: { name: string; code: string; description?: string }): Promise<ApiResult<Role>> {
    return http.post('/roles', data)
  },

  /** 更新角色（code 不可改） */
  update(id: number, data: { name?: string; description?: string }): Promise<ApiResult<Role>> {
    return http.put(`/roles/${id}`, data)
  },

  /** 删除角色 */
  delete(id: number): Promise<ApiResult<null>> {
    return http.delete(`/roles/${id}`)
  },

  /** 查询角色已分配的菜单 ID 列表 */
  getMenuIds(id: number): Promise<ApiResult<number[]>> {
    return http.get(`/roles/${id}/menu-ids`)
  },

  /** 为角色分配菜单（全量替换） */
  assignMenus(id: number, menuIds: number[]): Promise<ApiResult<null>> {
    return http.put(`/roles/${id}/menus`, { menuIds })
  },
}

/** 菜单管理 API */
export const menuApi = {
  /** 菜单树（管理用，含禁用/按钮） */
  getTree(): Promise<ApiResult<Menu[]>> {
    return http.get('/menus/tree')
  },

  /** 创建菜单 */
  create(data: MenuWrite): Promise<ApiResult<Menu>> {
    return http.post('/menus', data)
  },

  /** 更新菜单 */
  update(id: number, data: MenuWrite): Promise<ApiResult<Menu>> {
    return http.put(`/menus/${id}`, data)
  },

  /** 删除菜单 */
  delete(id: number): Promise<ApiResult<null>> {
    return http.delete(`/menus/${id}`)
  },

  /** 查询菜单按钮关联的接口 ID 列表 */
  getApiIds(id: number): Promise<ApiResult<number[]>> {
    return http.get(`/menus/${id}/api-ids`)
  },

  /** 为菜单按钮分配接口（全量替换） */
  assignApis(id: number, apiIds: number[]): Promise<ApiResult<null>> {
    return http.put(`/menus/${id}/apis`, { apiIds })
  },
}

/** 接口管理 API */
export const apiApi = {
  /** 获取全部有效接口（供菜单关联接口选择） */
  getList(): Promise<ApiResult<ApiItem[]>> {
    return http.get('/apis')
  },
}
