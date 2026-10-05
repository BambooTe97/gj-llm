import http from '@/api'
import type {
  AclDetail,
  AclGrant,
  Dataset,
  DatasetFile,
  PageData,
  PrincipalOption,
  TestRankedResult,
} from '@/api/types'

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
    rerankScoreThreshold?: number
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

  /** 检索测试(粗排 + reranker 精排,返回精排分/粗排分 + 阈值,预判线上是否采用) */
  testSearch(datasetId: string, query: string, topK = 3): Promise<ApiResponse<TestRankedResult>> {
    return http.post(`/v1/datasets/${datasetId}/test`, { query, topK })
  },

  // ==================== 共享设置（库级 RBAC） ====================

  /** 获取共享设置详情（可见性 + 授权列表） */
  getAcl(datasetId: string): Promise<ApiResponse<AclDetail>> {
    return http.get(`/v1/datasets/${datasetId}/acl`)
  },

  /** 切换可见性（PUBLIC / RESTRICTED） */
  updateVisibility(datasetId: string, visibility: 'PUBLIC' | 'RESTRICTED'): Promise<ApiResponse<null>> {
    return http.put(`/v1/datasets/${datasetId}/visibility`, { visibility })
  },

  /** 新增授权（user / role 主体） */
  grantAcl(datasetId: string, data: { principalType: 'user' | 'role'; principalId: string }): Promise<ApiResponse<AclGrant>> {
    return http.post(`/v1/datasets/${datasetId}/acl`, data)
  },

  /** 移除授权（按 ACL 行 ID） */
  revokeAcl(datasetId: string, aclId: string): Promise<ApiResponse<null>> {
    return http.delete(`/v1/datasets/${datasetId}/acl/${aclId}`)
  },

  /** 按关键词搜索用户（主体选择器，挂具体库路径下） */
  searchPrincipalUsers(datasetId: string, keyword: string): Promise<ApiResponse<PrincipalOption[]>> {
    return http.get(`/v1/datasets/${datasetId}/acl/users`, { params: { keyword } })
  },

  /** 角色列表（主体选择器，挂具体库路径下） */
  listPrincipalRoles(datasetId: string): Promise<ApiResponse<PrincipalOption[]>> {
    return http.get(`/v1/datasets/${datasetId}/acl/roles`)
  },
}
