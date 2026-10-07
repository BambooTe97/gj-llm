import http from '@/api'

/** 后端统一响应信封（com.gj.llm.common.web.R） */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 参数配置 —— 与后端 SysConfigEntity 对齐（id 为字符串：雪花 ID 超出 JS 安全整数） */
export interface SysConfigItem {
  id: string
  name: string
  configKey: string
  configValue: string
  builtIn: number
  remark?: string | null
  createdAt?: string
}

export interface SysConfigWrite {
  name: string
  configKey?: string
  configValue?: string
  remark?: string
}

/** 参数配置管理 —— /api/configs（权限点 system:config:*；getByKey 为全员消费端点） */
export const configApi = {
  /** 分页查询（keyword 匹配名称/键名） */
  async page(params: { page: number; size: number; keyword?: string }): Promise<{ records: SysConfigItem[]; total: number }> {
    const res = await http.get<ApiResult<{ records: SysConfigItem[]; total: number }>>('/configs', { params })
    const d = res.data.data
    return { records: d?.records ?? [], total: d?.total ?? 0 }
  },

  /** 按键名查询（业务消费入口；不存在时返回 null） */
  async getByKey(key: string): Promise<SysConfigItem | null> {
    const res = await http.get<ApiResult<SysConfigItem>>(`/configs/key/${key}`)
    return res.data.data ?? null
  },

  /** 创建参数 */
  async create(data: SysConfigWrite): Promise<void> {
    await http.post('/configs', data)
  },

  /** 更新参数（configKey 不可修改） */
  async update(id: string, data: SysConfigWrite): Promise<void> {
    await http.put(`/configs/${id}`, data)
  },

  /** 删除参数（内置参数后端拒绝） */
  async remove(id: string): Promise<void> {
    await http.delete(`/configs/${id}`)
  },
}
