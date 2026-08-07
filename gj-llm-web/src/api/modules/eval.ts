import http from '@/api'
import type { EvalQuery, RetrievalEvalTask } from '@/api/types'

export const evalApi = {
  /** 列出某知识库的评测用例 */
  listEvalQueries(datasetId: string): Promise<ApiResponse<EvalQuery[]>> {
    return http.get(`/v1/datasets/${datasetId}/eval-queries`)
  },

  /** 新增评测用例 */
  createEvalQuery(datasetId: string, data: EvalQuery): Promise<ApiResponse<EvalQuery>> {
    return http.post(`/v1/datasets/${datasetId}/eval-queries`, data)
  },

  /** 修改评测用例 */
  updateEvalQuery(datasetId: string, id: string | number, data: EvalQuery): Promise<ApiResponse<EvalQuery>> {
    return http.put(`/v1/datasets/${datasetId}/eval-queries/${id}`, data)
  },

  /** 删除评测用例 */
  deleteEvalQuery(datasetId: string, id: string | number): Promise<ApiResponse<null>> {
    return http.delete(`/v1/datasets/${datasetId}/eval-queries/${id}`)
  },

  /** 批量导入评测用例(外部 AI 按模板生成后灌入) */
  importEvalQueries(datasetId: string, queries: EvalQuery[]): Promise<ApiResponse<number>> {
    return http.post(`/v1/datasets/${datasetId}/eval-queries/import`, queries)
  },

  /** 测评(异步):提交任务;queryIds 空=全量,非空=选择性。任务按 datasetId 存,用 getEvalTask 轮询/恢复 */
  runEval(datasetId: string, queryIds?: (string | number)[]): Promise<ApiResponse<null>> {
    return http.post(`/v1/datasets/${datasetId}/eval`, { queryIds: queryIds && queryIds.length ? queryIds : undefined })
  },

  /** 查询该库最近一次测评任务状态/结果(轮询+重进恢复;进行中含 done/total,完成含 result,无任务返回 null) */
  getEvalTask(datasetId: string): Promise<ApiResponse<RetrievalEvalTask>> {
    return http.get(`/v1/datasets/${datasetId}/eval/task`)
  },
}
