import http from '@/api'
import type { VectorModel, PageData } from '@/api/types'

export const vectorModelApi = {
  /** 分页获取向量模型库 */
  getList(page = 1, pageSize = 10): Promise<ApiResponse<PageData<VectorModel>>> {
    return http.get('/vector-models', { params: { page, pageSize } })
  },

  /** 获取单个向量模型库 */
  getById(id: number): Promise<ApiResponse<VectorModel>> {
    return http.get(`/vector-models/${id}`)
  },

  /** 创建向量模型库 */
  create(data: { typeCode: string; typeName: string; collectionName: string; description?: string }): Promise<ApiResponse<VectorModel>> {
    return http.post('/vector-models', data)
  },

  /** 更新向量模型库 */
  update(id: number, data: { typeName?: string; collectionName?: string; description?: string; status?: number }): Promise<ApiResponse<VectorModel>> {
    return http.put(`/vector-models/${id}`, data)
  },

  /** 删除向量模型库 */
  deleteById(id: number): Promise<ApiResponse<null>> {
    return http.delete(`/vector-models/${id}`)
  },
}
