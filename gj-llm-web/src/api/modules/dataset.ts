import http from '@/api'
import type { Dataset, DatasetFile, PageData, SearchResultItem } from '@/api/types'

export const datasetApi = {
  /** 分页获取知识库列表 */
  getList(page = 1, pageSize = 10): Promise<ApiResponse<PageData<Dataset>>> {
    return http.get('/v1/datasets', { params: { page, pageSize } })
  },

  /** 获取单个知识库 */
  getById(id: string): Promise<ApiResponse<Dataset>> {
    return http.get(`/v1/datasets/${id}`)
  },

  /** 创建知识库 */
  create(data: {
    name: string
    description?: string
    embeddingModel: string
    vectorStoreType: string
    collectionName: string
    chunkSize?: number
    chunkOverlap?: number
  }): Promise<ApiResponse<Dataset>> {
    return http.post('/v1/datasets', data)
  },

  /** 更新知识库 */
  update(id: string, data: {
    name?: string
    description?: string
    embeddingModel?: string
    chunkSize?: number
    chunkOverlap?: number
  }): Promise<ApiResponse<Dataset>> {
    return http.put(`/v1/datasets/${id}`, data)
  },

  /** 删除知识库 */
  deleteById(id: string): Promise<ApiResponse<null>> {
    return http.delete(`/v1/datasets/${id}`)
  },

  /** 上传文件到知识库 */
  uploadDocument(datasetId: string, file: File): Promise<ApiResponse<DatasetFile>> {
    const formData = new FormData()
    formData.append('file', file)
    return http.post(`/v1/datasets/${datasetId}/documents/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /** 获取知识库下的文件列表 */
  getDocuments(datasetId: string, page = 1, pageSize = 10): Promise<ApiResponse<PageData<DatasetFile>>> {
    return http.get(`/v1/datasets/${datasetId}/documents`, { params: { page, pageSize } })
  },

  /** 删除知识库下的文件 */
  deleteDocument(datasetId: string, dfId: string): Promise<ApiResponse<null>> {
    return http.delete(`/v1/datasets/${datasetId}/documents/${dfId}`)
  },

  /** 重新解析文档 */
  reparseDocument(datasetId: string, dfId: string): Promise<ApiResponse<null>> {
    return http.post(`/v1/datasets/${datasetId}/documents/${dfId}/reparse`)
  },

  /** 检索测试 */
  testSearch(datasetId: string, query: string, topK = 3): Promise<ApiResponse<SearchResultItem[]>> {
    return http.post(`/v1/datasets/${datasetId}/test`, { query, topK })
  },
}
