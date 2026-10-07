import http from '@/api'

/** 后端统一响应信封（com.gj.llm.common.web.R） */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 部门 —— 与后端 SysDeptEntity 对齐（id 为字符串：雪花 ID 超出 JS 安全整数） */
export interface SysDept {
  id: string
  parentId: string | number
  ancestors?: string | null
  name: string
  sort?: number
  leader?: string | null
  phone?: string | null
  email?: string | null
  status: number
  children?: SysDept[]
}

export interface DeptWrite {
  parentId: string | number
  name: string
  sort?: number
  leader?: string
  phone?: string
  email?: string
  status?: number
}

/** 部门管理 —— /api/depts（权限点 system:dept:*） */
export const deptApi = {
  /** 部门树（全量，按 sort 升序） */
  async tree(): Promise<SysDept[]> {
    const res = await http.get<ApiResult<SysDept[]>>('/depts/tree')
    return res.data.data ?? []
  },

  /** 创建部门 */
  async create(data: DeptWrite): Promise<void> {
    await http.post('/depts', data)
  },

  /** 更新部门（parentId 变更时后端自动重算祖级链路） */
  async update(id: string, data: DeptWrite): Promise<void> {
    await http.put(`/depts/${id}`, data)
  },

  /** 删除部门（有下级或挂有用户时后端拒绝） */
  async remove(id: string): Promise<void> {
    await http.delete(`/depts/${id}`)
  },
}
