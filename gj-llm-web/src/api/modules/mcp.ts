import http from '@/api'
import type { AxiosResponse } from 'axios'
import type {
  McpApiKey,
  McpApiKeyCreate,
  McpAuditLog,
  McpConnectionTestResult,
  McpServerConfig,
  McpServerConfigWrite,
  McpTool,
  PageData,
} from '@/api/types'

/** API 响应类型别名：axios 响应（其 data 为统一 ApiResponse 包装），调用方用 res.data.data 取业务数据 */
type McpResult<T> = AxiosResponse<ApiResponse<T>>

/** MCP API Key 管理 API（对应后端 McpApiKeyController，/api/v1/mcp/api-keys） */
export const mcpApiKeyApi = {
  /** 分页查询 Key */
  getList(page = 1, pageSize = 10): Promise<McpResult<PageData<McpApiKey>>> {
    return http.get('/v1/mcp/api-keys', { params: { page, pageSize } })
  },

  /** 发放新 Key（fullKey 仅本次响应返回） */
  create(data: McpApiKeyCreate): Promise<McpResult<McpApiKey>> {
    return http.post('/v1/mcp/api-keys', data)
  },

  /** 启用/停用（status: 1=启用 0=停用） */
  updateStatus(id: string, status: number): Promise<McpResult<null>> {
    return http.put(`/v1/mcp/api-keys/${id}/status`, { status })
  },

  /** 吊销删除 */
  delete(id: string): Promise<McpResult<null>> {
    return http.delete(`/v1/mcp/api-keys/${id}`)
  },
}

/** 外部 MCP Server 管理 API（对应后端 McpServerConfigController，/api/v1/mcp/servers） */
export const mcpServerApi = {
  /** 分页查询外部服务 */
  getList(page = 1, pageSize = 10): Promise<McpResult<PageData<McpServerConfig>>> {
    return http.get('/v1/mcp/servers', { params: { page, pageSize } })
  },

  /** 新增服务（保存后立即按 enabled 建连） */
  create(data: McpServerConfigWrite): Promise<McpResult<McpServerConfig>> {
    return http.post('/v1/mcp/servers', data)
  },

  /** 更新服务（authHeaderValue 掩码/空 = 保留原值） */
  update(id: string, data: McpServerConfigWrite): Promise<McpResult<McpServerConfig>> {
    return http.put(`/v1/mcp/servers/${id}`, data)
  },

  /** 启停：启用即建连、停用即断连，运行时生效 */
  updateEnabled(id: string, enabled: boolean): Promise<McpResult<null>> {
    return http.put(`/v1/mcp/servers/${id}/enabled`, { enabled })
  },

  /** 删除服务 */
  delete(id: string): Promise<McpResult<null>> {
    return http.delete(`/v1/mcp/servers/${id}`)
  },

  /** 连接测试：临时建连 + MCP 握手 + 工具发现 */
  test(id: string): Promise<McpResult<McpConnectionTestResult>> {
    return http.post(`/v1/mcp/servers/${id}/test`)
  },

  /** 工具清单预览 */
  listTools(id: string): Promise<McpResult<McpTool[]>> {
    return http.get(`/v1/mcp/servers/${id}/tools`)
  },
}

/** MCP 审计日志 API（对应后端 McpAuditController，/api/v1/mcp/audits） */
export const mcpAuditApi = {
  /** 分页查询（筛选条件均可选） */
  getList(
    page = 1,
    pageSize = 10,
    filters?: { direction?: string; status?: string; toolName?: string; userId?: string },
  ): Promise<McpResult<PageData<McpAuditLog>>> {
    return http.get('/v1/mcp/audits', {
      params: {
        page,
        pageSize,
        direction: filters?.direction || undefined,
        status: filters?.status || undefined,
        toolName: filters?.toolName || undefined,
        userId: filters?.userId || undefined,
      },
    })
  },
}
