import http from '@/api'

/** 后端统一响应信封（com.gj.llm.common.web.R） */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 字典类型 —— 与后端 DictTypeEntity 对齐（id 为字符串：雪花 ID 超出 JS 安全整数） */
export interface DictTypeItem {
  id: string
  name: string
  type: string
  status: number
  remark?: string | null
  createdAt?: string
}

/** 字典数据 —— 与后端 DictDataEntity 对齐 */
export interface DictDataItem {
  id: string
  dictType: string
  label: string
  dictValue: string
  sort: number
  status: number
  remark?: string | null
}

export interface DictTypeWrite {
  name: string
  type: string
  status?: number
  remark?: string
}

export interface DictDataWrite {
  label: string
  dictValue: string
  sort?: number
  status?: number
  remark?: string
}

/** 字典类型管理 —— /api/dicts/types（权限点 system:dict:*） */
export const dictTypeApi = {
  /** 分页查询（keyword 匹配名称/类型编码） */
  async page(params: {
    page: number
    size: number
    keyword?: string
    status?: number
  }): Promise<{ records: DictTypeItem[]; total: number }> {
    const res = await http.get<ApiResult<{ records: DictTypeItem[]; total: number }>>('/dicts/types', { params })
    const d = res.data.data
    return { records: d?.records ?? [], total: d?.total ?? 0 }
  },

  /** 创建字典类型 */
  async create(data: DictTypeWrite): Promise<void> {
    await http.post('/dicts/types', data)
  },

  /** 更新字典类型（type 改名后端级联更新数据） */
  async update(id: string, data: DictTypeWrite): Promise<void> {
    await http.put(`/dicts/types/${id}`, data)
  },

  /** 删除字典类型（有数据时后端拒绝） */
  async remove(id: string): Promise<void> {
    await http.delete(`/dicts/types/${id}`)
  },
}

/** 字典数据管理 —— /api/dicts/datas（权限点 system:dict:*；listByType 为全员消费端点） */
export const dictDataApi = {
  /** 分页查询（按字典类型） */
  async page(params: {
    page: number
    size: number
    dictType: string
    keyword?: string
  }): Promise<{ records: DictDataItem[]; total: number }> {
    const res = await http.get<ApiResult<{ records: DictDataItem[]; total: number }>>('/dicts/datas', { params })
    const d = res.data.data
    return { records: d?.records ?? [], total: d?.total ?? 0 }
  },

  /** 按类型查询启用数据（业务消费入口，sort 升序） */
  async listByType(type: string): Promise<DictDataItem[]> {
    const res = await http.get<ApiResult<DictDataItem[]>>(`/dicts/datas/type/${type}`)
    return res.data.data ?? []
  },

  /** 创建字典数据 */
  async create(dictType: string, data: DictDataWrite): Promise<void> {
    await http.post('/dicts/datas', { dictType, ...data })
  },

  /** 更新字典数据 */
  async update(id: string, data: DictDataWrite): Promise<void> {
    await http.put(`/dicts/datas/${id}`, data)
  },

  /** 删除字典数据 */
  async remove(id: string): Promise<void> {
    await http.delete(`/dicts/datas/${id}`)
  },
}
