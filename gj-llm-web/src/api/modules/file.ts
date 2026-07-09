import http from '@/api'
import type { FileRecord } from '@/api/types'

/** 文件列表分页响应 */
interface FileListData {
  list: FileRecord[]
  total: number
  page: number
  pageSize: number
}

export const fileApi = {
  /** 上传单个文件 */
  upload(file: File): Promise<ApiResponse<FileRecord>> {
    const formData = new FormData()
    formData.append('file', file)
    // 不要手动设置 Content-Type，浏览器会自动设置带 boundary 的 multipart/form-data
    return http.post('/files/upload', formData)
  },

  /** 分页获取文件列表 */
  getList(page = 1, pageSize = 10): Promise<ApiResponse<FileListData>> {
    return http.get('/files', { params: { page, pageSize } })
  },

  /** 删除文件（按记录 ID） */
  deleteById(id: string): Promise<ApiResponse<null>> {
    return http.delete(`/files/${id}`)
  },

  /** 获取文件下载地址（按记录 ID） */
  getDownloadUrl(id: string): string {
    return `${import.meta.env.VITE_API_BASE_URL}/files/${id}`
  },

  /** 下载文件（通过 axios 携带认证 Token） */
  async download(id: string, fileName: string): Promise<void> {
    const response = await http.get(`/files/${id}`, {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(response.data as Blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.click()
    window.URL.revokeObjectURL(url)
  },
}
