<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadInstance, UploadRawFile } from 'element-plus'
import { datasetApi } from '@/api/modules/dataset'
import { evalApi } from '@/api/modules/eval'
import type { Dataset, DatasetFile, RankedTestItem, EvalQuery, EvalResultItem, RetrievalEvalResult } from '@/api/types'
import {
  UploadFilled, Refresh, Document, Loading, RefreshRight,
  Delete, QuestionFilled, Search,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const datasetId = route.params.id as string

// ---- 知识库信息 ----
const dataset = ref<Dataset | null>(null)
const loadingDataset = ref(false)

// ---- 文档列表 ----
const docList = ref<DatasetFile[]>([])
const docLoading = ref(false)
const docPage = ref(1)
const docPageSize = ref(10)
const docTotal = ref(0)
const uploadRef = ref<UploadInstance>()
const uploading = ref(false)

// ---- 检索测试 ----
const activeTab = ref<'files' | 'search' | 'eval'>('files')
const searchQuery = ref('')
const searchTopK = ref(3)
const searching = ref(false)
// 检索测试:始终走 hybrid 粗排 + reranker 精排,恒定展示精排分(主)+向量相似度(Milvus 余弦,辅)
const rankedResults = ref<RankedTestItem[]>([])
const rerankerAvailable = ref(false)
const rerankScoreThreshold = computed(() => dataset.value?.rerankScoreThreshold ?? 0)

// ---- 检索评测 ----
const evalQueries = ref<EvalQuery[]>([])
const evalLoading = ref(false)
const evalRunning = ref(false)
const evalResult = ref<RetrievalEvalResult | null>(null)
const evalLoaded = ref(false)
const evalDialogVisible = ref(false)
const evalEditing = ref<EvalQuery | null>(null)
const evalForm = ref<EvalQuery>({ query: '', expectedSource: '', expectedSnippet: '' })
const importDialogVisible = ref(false)
const importText = ref('')
const showPrompt = ref(false)
// 选择性测评:勾选的用例 id(空=全量) + 进度 + 轮询定时器
const selectedIds = ref<(string | number)[]>([])
/** 受控展开的用例行(配合 expand-row-keys,支持全部展开/折叠) */
const expandedEvalRows = ref<(string | number)[]>([])
const evalProgress = ref({ done: 0, total: 0 })
let evalPollTimer: ReturnType<typeof setInterval> | null = null

/** 外部 AI 生成评测集的提示词模板(强制逐字摘录 + 混合口语 + JSON 格式) */
const promptTemplate = `你是一个 RAG 评测集生成助手。我会给你知识库的文档片段，请为每个片段生成 2-3 个检索测试问题，并标注期望命中的依据。

严格要求：
1. expectedSnippet 必须从该片段原文中"逐字摘录"一句（不能改写、不能总结），因为它要做子串匹配，改写会导致判定失效。
2. 问题要混合三种风格：正式书面、口语化、模糊简短——尤其要有口语化问题（如"这玩意儿咋配"）。
3. expectedSource 填该片段的来源文件名（可只填部分关键词）。
4. 只输出 JSON 数组，格式：[{"query":"...","expectedSource":"...","expectedSnippet":"..."}]，不要任何额外说明。

文档片段如下：`

const thresholdPoints = [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]
/** 阈值扫描:effective recall(T) = 命中且期望文档精排分 ≥ T 的占比 */
const thresholdScan = computed(() => {
  if (!evalResult.value || !evalResult.value.total) return []
  const total = evalResult.value.total
  return thresholdPoints.map((t) => ({
    threshold: t,
    recall: evalResult.value!.items.filter((it) => it.found && it.expectedScore >= t).length / total,
  }))
})
/** 当前配置的精排阈值(取自知识库配置,采纳后实时更新;默认 0.3 兜底) */
const currentThreshold = computed(() => dataset.value?.rerankScoreThreshold ?? 0.3)
/** 系统计算的推荐阈值(有效召回率≥95%·Recall@5 的最高阈值) */
const recommendedThreshold = computed(() => evalResult.value?.recommendedThreshold ?? 0)
/** 手动调整阈值的本地输入(跟随当前阈值,可被"采纳"填入推荐值) */
const manualThreshold = ref(0.3)
watch(currentThreshold, (v) => { manualThreshold.value = v }, { immediate: true })
/** 阈值保存中(采纳/手动保存共用 loading) */
const adopting = ref(false)
/** 评测结果按用例 ID 索引,供用例行合入结果 */
const evalResultMap = computed(() => {
  const map = new Map<string | number, EvalResultItem>()
  evalResult.value?.items.forEach((it) => map.set(it.queryId, it))
  return map
})
/** 本次测评命中数 */
const evalHitCount = computed(() => evalResult.value?.items.filter((it) => it.found).length ?? 0)
/** 有结果可展开的用例行(只展开跑过的,未参与的不展开) */
const evalResultRows = computed(() => evalQueries.value.filter((r) => r.id != null && evalResultMap.value.get(r.id)))
/** 是否已全部展开 */
const allExpanded = computed(() =>
  evalResultRows.value.length > 0 &&
  evalResultRows.value.every((r) => expandedEvalRows.value.some((id) => id === r.id)),
)
/** 评测进度百分比(done/total) */
const evalProgressPct = computed(() => {
  const { done, total } = evalProgress.value
  return total > 0 ? Math.round((done / total) * 100) : 0
})

// ---- 自动轮询（文件处理状态） ----
const pollingEnabled = ref(true)
const pollingInterval = ref(30) // 秒
let pollingTimer: ReturnType<typeof setInterval> | null = null

function hasProcessingFiles(): boolean {
  return docList.value.some((f) => f.status === 'PENDING' || f.status === 'PROCESSING')
}

function startPolling() {
  if (pollingTimer) return // 已经在轮询中
  if (!pollingEnabled.value) return
  pollingTimer = setInterval(async () => {
    if (!hasProcessingFiles()) {
      stopPolling()
      return
    }
    // 静默刷新（不设置 loading 状态，避免表格闪烁）
    try {
      const res = await datasetApi.getDocuments(datasetId, docPage.value, docPageSize.value)
      const data = res.data.data
      docList.value = data?.records || []
      docTotal.value = data?.total || 0
      // 同步刷新知识库统计数据（静默，不触发 loading）
      const dsRes = await datasetApi.getById(datasetId)
      dataset.value = dsRes.data.data
    } catch {
      // 静默忽略轮询错误
    }
  }, pollingInterval.value * 1000)
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

function restartPolling() {
  stopPolling()
  if (hasProcessingFiles()) startPolling()
}

// 开关切换时：开 → 启动，关 → 停止
watch(pollingEnabled, (on) => {
  if (on) {
    if (hasProcessingFiles()) startPolling()
  } else {
    stopPolling()
  }
})

// 间隔变化时重新启动定时器
watch(pollingInterval, () => {
  if (pollingEnabled.value && pollingTimer) {
    restartPolling()
  }
})

// ---- 格式化 ----
function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

function formatTime(dateStr: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '排队中', PROCESSING: '向量化中', COMPLETED: '完成', FAILED: '失败',
  }
  return map[status] || status
}

function statusType(status: string): 'info' | 'warning' | 'success' | 'danger' | '' {
  const map: Record<string, 'info' | 'warning' | 'success' | 'danger' | ''> = {
    PENDING: 'info', PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger',
  }
  return map[status] || 'info'
}

function dsStatusLabel(status: string): string {
  const map: Record<string, string> = { READY: '就绪', INDEXING: '索引中', ERROR: '异常' }
  return map[status] || status
}

// ---- 加载知识库信息 ----
async function loadDataset() {
  loadingDataset.value = true
  try {
    const res = await datasetApi.getById(datasetId)
    dataset.value = res.data.data
  } finally {
    loadingDataset.value = false
  }
}

// ---- 加载文档列表 ----
async function loadDocuments() {
  docLoading.value = true
  try {
    const res = await datasetApi.getDocuments(datasetId, docPage.value, docPageSize.value)
    const data = res.data.data
    docList.value = data?.records || []
    docTotal.value = data?.total || 0
  } finally {
    docLoading.value = false
  }
}

function handleDocPageChange(page: number) { docPage.value = page; loadDocuments() }
function handleDocSizeChange(size: number) { docPageSize.value = size; docPage.value = 1; loadDocuments() }

// ---- 上传 ----
async function handleUpload(options: { file: UploadRawFile }) {
  uploading.value = true
  try {
    await datasetApi.uploadDocument(datasetId, options.file as File)
    ElMessage.success(`"${options.file.name}" 上传成功，正在排队处理...`)
    docPage.value = 1
    await loadDocuments()
    await loadDataset() // 刷新统计数据
    startPolling()      // 启动轮询，跟踪向量化进度
  } catch { /* 拦截器统一处理 */ } finally {
    uploading.value = false
    uploadRef.value?.clearFiles()
  }
}

// ---- 删除文档 ----
async function handleDeleteDoc(row: DatasetFile) {
  try {
    await datasetApi.deleteDocument(datasetId, row.id)
    ElMessage.success(`"${row.fileName}" 已删除`)
    if (docList.value.length === 1 && docPage.value > 1) docPage.value--
    await loadDocuments()
    await loadDataset()
  } catch { /* 拦截器统一处理 */ }
}

// ---- 检索测试 ----
async function handleSearch() {
  if (!searchQuery.value.trim()) return
  searching.value = true
  rankedResults.value = []
  try {
    const res = await datasetApi.testSearch(datasetId, searchQuery.value, searchTopK.value)
    const data = res.data.data
    rankedResults.value = data?.items || []
    rerankerAvailable.value = data?.rerankerAvailable ?? false
  } finally {
    searching.value = false
  }
}

// ---- 重新解析 ----
async function handleReParse(row: DatasetFile) {
  try {
    await datasetApi.reparseDocument(datasetId, row.id)
    ElMessage.success('已触发重新解析')
    await loadDocuments()
    startPolling()
  } catch { /* 拦截器统一处理 */ }
}

// ---- 检索评测 ----
async function loadEvalQueries() {
  evalLoading.value = true
  try {
    const res = await evalApi.listEvalQueries(datasetId)
    evalQueries.value = res.data.data || []
  } finally {
    evalLoading.value = false
  }
}

function openCreateDialog() {
  evalEditing.value = null
  evalForm.value = { query: '', expectedSource: '', expectedSnippet: '' }
  evalDialogVisible.value = true
}

function openEditDialog(row: EvalQuery) {
  evalEditing.value = row
  evalForm.value = { query: row.query, expectedSource: row.expectedSource, expectedSnippet: row.expectedSnippet }
  evalDialogVisible.value = true
}

// 从检索测试结果一键沉淀为评测用例:query=当前搜索词,期望依据取自该结果
function markAsExpected(item: RankedTestItem) {
  evalEditing.value = null
  evalForm.value = {
    query: searchQuery.value,
    expectedSource: item.source || '',
    expectedSnippet: item.content,
  }
  evalDialogVisible.value = true
}

async function saveEvalQuery() {
  if (!evalForm.value.query.trim()) {
    ElMessage.warning('查询不能为空')
    return
  }
  try {
    if (evalEditing.value) {
      await evalApi.updateEvalQuery(datasetId, evalEditing.value.id!, evalForm.value)
      ElMessage.success('已更新')
    } else {
      await evalApi.createEvalQuery(datasetId, evalForm.value)
      ElMessage.success('已新增')
    }
    evalDialogVisible.value = false
    await loadEvalQueries()
  } catch { /* 拦截器统一处理 */ }
}

async function deleteEvalQuery(row: EvalQuery) {
  try {
    await evalApi.deleteEvalQuery(datasetId, row.id!)
    ElMessage.success('已删除')
    await loadEvalQueries()
  } catch { /* 拦截器统一处理 */ }
}

function openImportDialog() {
  importText.value = ''
  importDialogVisible.value = true
}

async function doImport() {
  let arr: EvalQuery[]
  try {
    arr = JSON.parse(importText.value)
    if (!Array.isArray(arr)) throw new Error('not array')
  } catch {
    ElMessage.error('JSON 格式错误，需为数组')
    return
  }
  try {
    const res = await evalApi.importEvalQueries(datasetId, arr)
    ElMessage.success(`导入 ${res.data.data} 条`)
    importDialogVisible.value = false
    await loadEvalQueries()
  } catch { /* 拦截器统一处理 */ }
}

async function runEval() {
  if (evalQueries.value.length === 0) {
    ElMessage.warning('请先添加或导入评测用例')
    return
  }
  evalRunning.value = true
  evalResult.value = null
  expandedEvalRows.value = []
  const total = selectedIds.value.length || evalQueries.value.length
  evalProgress.value = { done: 0, total }
  try {
    await evalApi.runEval(datasetId, selectedIds.value)
    startEvalPolling()
  } catch {
    evalRunning.value = false
  }
}

/** 用例表多选变更:维护选中 id(空=跑全部) */
function handleEvalSelectionChange(rows: EvalQuery[]) {
  selectedIds.value = rows.map((r) => r.id).filter((v): v is string | number => v != null)
}

/** 行展开受控同步:用户单行点箭头时同步 expandedEvalRows */
function handleEvalExpandChange(_row: EvalQuery, expandedRows: EvalQuery[]) {
  expandedEvalRows.value = expandedRows.map((r) => r.id).filter((v): v is string | number => v != null)
}

/** 全部展开/折叠(只操作有结果的行) */
function toggleExpandAll() {
  expandedEvalRows.value = allExpanded.value
    ? []
    : evalResultRows.value.map((r) => r.id).filter((v): v is string | number => v != null)
}

/** 轮询评测任务状态:完成取 result,失败提示,异常静默下一轮重试 */
function startEvalPolling() {
  if (evalPollTimer) return
  evalPollTimer = setInterval(async () => {
    try {
      const res = await evalApi.getEvalTask(datasetId)
      const task = res.data.data
      if (!task) { stopEvalPolling(); evalRunning.value = false; return }
      evalProgress.value = { done: task.done, total: task.total }
      if (task.status === 'COMPLETED') {
        evalResult.value = task.result
        stopEvalPolling()
        evalRunning.value = false
      } else if (task.status === 'FAILED') {
        ElMessage.error(task.errorMessage || '评测失败')
        stopEvalPolling()
        evalRunning.value = false
      }
    } catch { /* 静默忽略轮询错误,下一轮重试 */ }
  }, 2000)
}

function stopEvalPolling() {
  if (evalPollTimer) {
    clearInterval(evalPollTimer)
    evalPollTimer = null
  }
}

/** 重进页面:按 datasetId 从 Redis 恢复上次测评结果(2h 内有效),无需前端记 taskId */
async function restoreLastEval() {
  try {
    const res = await evalApi.getEvalTask(datasetId)
    const task = res.data.data
    if (!task) return
    if (task.status === 'COMPLETED' && task.result) {
      evalResult.value = task.result
      evalProgress.value = { done: task.done, total: task.total }
    } else if (task.status === 'RUNNING' || task.status === 'PENDING') {
      evalRunning.value = true
      evalProgress.value = { done: task.done, total: task.total }
      startEvalPolling()
    }
  } catch { /* 无最近任务,静默 */ }
}

/** 采纳推荐阈值:写入知识库配置,刷新 dataset 使标线即时移动 */
async function adoptRecommended() {
  adopting.value = true
  try {
    await datasetApi.update(datasetId, { rerankScoreThreshold: recommendedThreshold.value })
    ElMessage.success(`已采纳推荐阈值 ${(recommendedThreshold.value * 100).toFixed(0)}%`)
    await loadDataset()
  } catch { /* 拦截器统一处理 */ } finally {
    adopting.value = false
  }
}

/** 手动保存阈值:写入知识库配置 */
async function saveManualThreshold() {
  adopting.value = true
  try {
    await datasetApi.update(datasetId, { rerankScoreThreshold: manualThreshold.value })
    ElMessage.success('阈值已更新')
    await loadDataset()
  } catch { /* 拦截器统一处理 */ } finally {
    adopting.value = false
  }
}

onUnmounted(() => stopEvalPolling())

function copyPrompt() {
  navigator.clipboard?.writeText(promptTemplate)
  ElMessage.success('已复制提示词，粘贴给外部 AI 并附上文档片段')
}

// 评测 tab 首次激活时懒加载用例
watch(activeTab, (tab) => {
  if (tab === 'eval' && !evalLoaded.value) {
    evalLoaded.value = true
    loadEvalQueries()
    restoreLastEval()
  }
})

onMounted(async () => {
  await loadDataset()
  await loadDocuments()
  // 如果自动刷新开启且有处理中的文件，启动轮询
  if (pollingEnabled.value && hasProcessingFiles()) startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="ds-detail">
    <!-- 面包屑 -->
    <div class="ds-breadcrumb">
      <el-breadcrumb separator=">">
        <el-breadcrumb-item :to="{ path: '/datasets' }">知识库列表</el-breadcrumb-item>
        <el-breadcrumb-item>{{ dataset?.name || '加载中...' }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="ds-detail__body">
      <!-- 左侧：配置概览 -->
      <aside class="ds-sidebar" v-loading="loadingDataset">
        <template v-if="dataset">
          <div class="ds-sidebar__header">
            <div class="ds-sidebar__name">{{ dataset.name }}</div>
            <el-tag
              :type="dataset.status === 'READY' ? 'success' : dataset.status === 'INDEXING' ? 'warning' : 'danger'"
              size="small"
              effect="light"
            >
              {{ dsStatusLabel(dataset.status) }}
            </el-tag>
          </div>

          <div class="ds-sidebar__desc" v-if="dataset.description">
            {{ dataset.description }}
          </div>

          <div class="ds-sidebar__section">
            <div class="ds-sidebar__section-title">模型与存储</div>
            <div class="ds-sidebar__row">
              <span class="ds-sidebar__label">Embedding</span>
              <span class="ds-sidebar__value">{{ dataset.embeddingModel }}</span>
            </div>
            <div class="ds-sidebar__row">
              <span class="ds-sidebar__label">向量库</span>
              <span class="ds-sidebar__value">{{ dataset.vectorStoreType }}</span>
            </div>
            <div class="ds-sidebar__row">
              <span class="ds-sidebar__label">集合名称</span>
              <code class="ds-sidebar__code">{{ dataset.collectionName }}</code>
            </div>
          </div>

          <div class="ds-sidebar__section">
            <div class="ds-sidebar__section-title">切片参数</div>
            <div class="ds-sidebar__row">
              <span class="ds-sidebar__label">Chunk Size</span>
              <span class="ds-sidebar__value">{{ dataset.chunkSize }}</span>
            </div>
            <div class="ds-sidebar__row">
              <span class="ds-sidebar__label">Overlap</span>
              <span class="ds-sidebar__value">{{ dataset.chunkOverlap }}</span>
            </div>
          </div>

          <div class="ds-sidebar__section">
            <div class="ds-sidebar__section-title">检索参数</div>
            <div class="ds-sidebar__row">
              <span class="ds-sidebar__label">精排阈值</span>
              <span class="ds-sidebar__value">{{ (dataset.rerankScoreThreshold * 100).toFixed(0) }}%</span>
            </div>
          </div>

          <div class="ds-sidebar__section">
            <div class="ds-sidebar__section-title">统计</div>
            <div class="ds-sidebar__stats">
              <div class="ds-sidebar__stat">
                <span class="ds-sidebar__stat-num">{{ dataset.docCount }}</span>
                <span class="ds-sidebar__stat-label">文档数</span>
              </div>
              <div class="ds-sidebar__stat">
                <span class="ds-sidebar__stat-num">{{ dataset.segmentCount }}</span>
                <span class="ds-sidebar__stat-label">向量数</span>
              </div>
            </div>
          </div>
        </template>
      </aside>

      <!-- 右侧：文件管理 + 检索测试 -->
      <main class="ds-main">
        <!-- Tab 切换 -->
        <el-tabs v-model="activeTab" class="ds-tabs">
          <el-tab-pane label="文件管理" name="files">
            <!-- 上传区 -->
            <div class="glass-card ds-upload-card">
              <div class="glass-card__body">
                <el-upload
                  ref="uploadRef"
                  drag
                  :show-file-list="true"
                  :http-request="handleUpload"
                  :limit="1"
                  accept=".pdf,.doc,.docx,.txt,.md,.csv,.xls,.xlsx,.ppt,.pptx"
                >
                  <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                  <div class="el-upload__text">
                    将文件拖到此处，或 <em>点击选择文件</em>
                  </div>
                  <template #tip>
                    <div class="el-upload__tip">
                      支持 PDF, TXT, MD, DOCX 等格式，上传后自动进行向量化处理
                    </div>
                  </template>
                </el-upload>
              </div>
            </div>

            <!-- 文件列表 -->
            <div class="glass-card ds-file-list-card">
              <div class="glass-card__header">
                <span>文件列表（{{ docTotal }}）</span>
                <div class="polling-controls">
                  <el-switch
                    v-model="pollingEnabled"
                    size="small"
                    active-text="自动刷新"
                    inactive-text="关闭"
                  />
                  <template v-if="pollingEnabled">
                    <span class="polling-label">间隔</span>
                    <el-select
                      v-model="pollingInterval"
                      size="small"
                      style="width: 80px"
                    >
                      <el-option :value="5" label="5s" />
                      <el-option :value="15" label="15s" />
                      <el-option :value="30" label="30s" />
                    </el-select>
                  </template>
                  <el-button text size="small" @click="loadDocuments">
                    <el-icon><Refresh /></el-icon>
                    刷新
                  </el-button>
                </div>
              </div>
              <div class="glass-card__body">
                <el-table
                  :data="docList"
                  v-loading="docLoading"
                  stripe
                  style="width: 100%"
                  empty-text="暂无文件，请上传"
                >
                  <el-table-column label="文件名" min-width="200">
                    <template #default="{ row }">
                      <div class="doc-name-cell">
                        <el-icon><Document /></el-icon>
                        <span>{{ row.fileName }}</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="大小" width="100" align="right">
                    <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
                  </el-table-column>
                  <el-table-column label="上传时间" width="160" align="center">
                    <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
                  </el-table-column>
                  <el-table-column label="处理状态" width="200" align="center">
                    <template #default="{ row }">
                      <el-tooltip
                        v-if="row.status === 'FAILED' && row.errorMessage"
                        :content="row.errorMessage"
                        placement="top"
                      >
                        <el-tag :type="statusType(row.status)" size="small" effect="light">
                          {{ statusLabel(row.status) }}
                        </el-tag>
                      </el-tooltip>
                      <template v-else-if="row.status === 'PROCESSING'">
                        <div class="progress-cell">
                          <el-tag type="warning" size="small" effect="light">
                            <el-icon class="is-loading"><Loading /></el-icon>
                            {{ statusLabel(row.status) }}
                          </el-tag>
                          <el-progress
                            :percentage="row.progressPercent || 0"
                            :stroke-width="4"
                            :show-text="false"
                            style="width: 100%; margin-top: 4px"
                          />
                          <span class="progress-step" v-if="row.currentStep">{{ row.currentStep }}</span>
                        </div>
                      </template>
                      <el-tag v-else :type="statusType(row.status)" size="small" effect="light">
                        {{ statusLabel(row.status) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="切片数" width="80" align="center">
                    <template #default="{ row }">{{ row.segmentCount || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="操作" min-width="160" align="center" fixed="right">
                    <template #default="{ row }">
                      <div class="action-btns">
                        <el-button
                          v-if="row.status === 'FAILED' || row.status === 'COMPLETED'"
                          text size="small" type="primary"
                          @click="handleReParse(row)"
                        >
                          <el-icon><RefreshRight /></el-icon>
                          重新解析
                        </el-button>
                        <el-popconfirm
                          title="确定要删除该文件吗？将同时移除向量数据。"
                          confirm-button-text="删除"
                          cancel-button-text="取消"
                          @confirm="handleDeleteDoc(row)"
                        >
                          <template #reference>
                            <el-button text size="small" type="danger">
                              <el-icon><Delete /></el-icon>
                              删除
                            </el-button>
                          </template>
                        </el-popconfirm>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>

                <div class="ds-file-pagination" v-if="docTotal > 0">
                  <el-pagination
                    v-model:current-page="docPage"
                    v-model:page-size="docPageSize"
                    :total="docTotal"
                    :page-sizes="[5, 10, 20, 50]"
                    layout="total, sizes, prev, pager, next, jumper"
                    background
                    size="small"
                    @current-change="handleDocPageChange"
                    @size-change="handleDocSizeChange"
                  />
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- 检索测试 -->
          <el-tab-pane label="检索测试" name="search">
            <div class="glass-card ds-search-card">
              <div class="glass-card__body">
                <div class="ds-search__input-row">
                  <div class="ds-search__input-row-inner">
                    <div class="ds-search__topk-row">
                      <span class="ds-search__topk-label">Top K</span>
                      <el-select v-model="searchTopK" style="width: 80px" size="small">
                        <el-option :value="3" label="3" />
                        <el-option :value="5" label="5" />
                        <el-option :value="10" label="10" />
                      </el-select>
                      <el-tooltip
                        placement="top"
                        effect="dark"
                        raw-content
                        content="<div style='max-width:220px;line-height:1.6;font-size:13px;'><b>Top K</b> 控制每次检索返回的<b>最相似文档片段数量</b>。<br/>例如 Top 5 表示返回相似度最高的 5 个切片。<br/>用于控制 RAG 召回结果的丰富程度，K 值越大上下文越多。</div>"
                      >
                        <span class="ds-search__help-icon">
                          <el-icon><QuestionFilled /></el-icon>
                        </span>
                      </el-tooltip>
                    </div>
                    <el-input
                      v-model="searchQuery"
                      placeholder="输入问题，测试 RAG 召回效果..."
                      clearable
                      @keyup.enter="handleSearch"
                      style="flex: 1"
                    />
                  </div>
                  <el-button type="primary" :disabled="searching" @click="handleSearch">
                    <span :class="{ 'is-loading': searching }" style="display: inline-flex">
                      <el-icon><Search /></el-icon>
                    </span>
                    检索
                  </el-button>
                </div>

                <!-- 检索结果:精排分为主(阈值标"会采用/低于阈值"),向量相似度(Milvus 余弦)为辅;仅 BM25 命中者相似度显示 - -->
                <div class="ds-search__reranker-status" v-if="rankedResults.length > 0">
                  <el-tag :type="rerankerAvailable ? 'success' : 'danger'" size="small" effect="dark" round>
                    reranker：{{ rerankerAvailable ? '正常' : '未生效（降级粗排）' }}
                  </el-tag>
                  <span class="ds-search__threshold-hint">
                    精排阈值 {{ (rerankScoreThreshold * 100).toFixed(0) }}% · 达标即在线上对话采用
                  </span>
                </div>
                <div class="ds-search__results" v-if="rankedResults.length > 0">
                  <div v-for="item in rankedResults" :key="item.rank" class="ds-search__item">
                    <div class="ds-search__item-header">
                      <div class="ds-search__item-left">
                        <el-tag size="small" effect="dark" round>#{{ item.rank }}</el-tag>
                        <el-tag
                          size="small"
                          effect="light"
                          round
                          :type="item.rerankScore >= rerankScoreThreshold ? 'success' : 'info'"
                        >
                          {{ item.rerankScore >= rerankScoreThreshold ? '会采用' : '低于阈值' }}
                        </el-tag>
                        <el-tag v-if="item.coarseScore <= 0" size="small" effect="plain" round type="warning">
                          BM25 命中
                        </el-tag>
                      </div>
                      <span class="ds-search__score">
                        精排 {{ (item.rerankScore * 100).toFixed(1) }}% ｜ 向量相似度 {{ item.coarseScore > 0 ? (item.coarseScore * 100).toFixed(1) + '%' : '-' }}
                      </span>
                    </div>
                    <div class="ds-search__content">{{ item.content }}</div>
                    <div class="ds-search__meta">
                      <span class="ds-search__meta-tag" v-if="item.source">source: {{ item.source }}</span>
                      <el-button text size="small" type="primary" @click="markAsExpected(item)">标为期望答案</el-button>
                    </div>
                  </div>
                </div>
                <div class="ds-search__empty" v-else-if="!searching && searchQuery">暂无结果</div>

                <div class="ds-search__hint" v-if="!searchQuery && rankedResults.length === 0">
                  <el-icon :size="36"><Search /></el-icon>
                  <span>输入查询内容测试知识库的检索效果</span>
                  <span class="ds-search__hint-sub">不需启动对话即可验证 RAG 召回质量</span>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- 检索评测 -->
          <el-tab-pane label="检索评测" name="eval">
            <div class="glass-card ds-eval-card">
              <div class="glass-card__header">
                <span>测评用例（{{ evalQueries.length }}）</span>
                <div class="ds-eval__actions">
                  <el-button size="small" type="primary" :disabled="evalRunning" @click="runEval">
                    {{ evalRunning ? `测评中 ${evalProgress.done}/${evalProgress.total}` : '测评' }}
                  </el-button>
                  <el-button size="small" :disabled="evalRunning" @click="openImportDialog">导入</el-button>
                  <el-button size="small" :disabled="evalRunning" @click="openCreateDialog">新增</el-button>
                  <el-button size="small" text @click="showPrompt = !showPrompt">AI 生成指引</el-button>
                  <span v-if="selectedIds.length" class="ds-eval__selected">已选 {{ selectedIds.length }}</span>
                </div>
              </div>
              <div class="glass-card__body">
                <!-- 测评进度 -->
                <div class="ds-eval__progress" v-if="evalRunning">
                  <span class="ds-eval__progress-text">测评中 {{ evalProgress.done }}/{{ evalProgress.total }}</span>
                  <el-progress :percentage="evalProgressPct" :stroke-width="6" :show-text="false" status="success" />
                </div>
                <!-- 外部 AI 生成指引 -->
                <div class="ds-eval__prompt" v-if="showPrompt">
                  <div class="ds-eval__prompt-head">
                    <span>用外部强 AI（GPT/Claude/GLM）按以下提示词生成，再走「导入」灌入</span>
                    <el-button size="small" @click="copyPrompt">复制提示词</el-button>
                  </div>
                  <pre class="ds-eval__prompt-text">{{ promptTemplate }}</pre>
                </div>

                <!-- 聚合指标 + 阈值扫描(跑完显示,贴表格上方) -->
                <div class="ds-eval__summary" v-if="evalResult">
                  <div class="ds-eval__summary-head">
                    <div class="ds-eval__metrics">
                      <div class="ds-eval__metric">
                        <span class="ds-eval__metric-num">{{ (evalResult.recallAtK * 100).toFixed(1) }}%</span>
                        <span class="ds-eval__metric-label">Recall@5</span>
                      </div>
                      <div class="ds-eval__metric">
                        <span class="ds-eval__metric-num">{{ evalResult.mrr.toFixed(3) }}</span>
                        <span class="ds-eval__metric-label">MRR</span>
                      </div>
                      <div class="ds-eval__metric">
                        <span class="ds-eval__metric-num">{{ evalHitCount }}/{{ evalResult.total }}</span>
                        <span class="ds-eval__metric-label">命中</span>
                      </div>
                    </div>
                    <el-button text size="small" class="ds-eval__expand-toggle" @click="toggleExpandAll">
                      {{ allExpanded ? '折叠全部' : '展开全部' }}
                    </el-button>
                  </div>
                  <el-collapse class="ds-eval__scan-collapse">
                    <el-collapse-item name="scan">
                      <template #title>
                        <span class="ds-eval__scan-title">阈值扫描 · effective recall = 命中且期望文档分 ≥ 阈值 的占比（找 recall 开始塌的拐点定阈值）</span>
                      </template>
                      <div class="ds-eval__scan-row" v-for="t in thresholdScan" :key="t.threshold">
                        <span class="ds-eval__scan-label" :class="{ 'is-current': Math.abs(t.threshold - currentThreshold) < 0.001, 'is-recommended': Math.abs(t.threshold - recommendedThreshold) < 0.001 }">
                          {{ (t.threshold * 100).toFixed(0) }}%{{ Math.abs(t.threshold - currentThreshold) < 0.001 ? '（当前）' : Math.abs(t.threshold - recommendedThreshold) < 0.001 ? '（推荐）' : '' }}
                        </span>
                        <div class="ds-eval__scan-bar-bg">
                          <div class="ds-eval__scan-bar" :style="{ width: (t.recall * 100) + '%' }"></div>
                        </div>
                        <span class="ds-eval__scan-value">{{ (t.recall * 100).toFixed(0) }}%</span>
                      </div>
                    </el-collapse-item>
                  </el-collapse>

                  <!-- 推荐阈值 + 采纳/手动调整 -->
                  <div class="ds-eval__threshold-panel" v-if="evalResult">
                    <div class="ds-eval__threshold-rec" v-if="evalResult.recommendationReason">
                      <div class="ds-eval__threshold-rec-head">
                        <span class="ds-eval__threshold-rec-label">推荐阈值</span>
                        <span class="ds-eval__threshold-rec-value">{{ (recommendedThreshold * 100).toFixed(0) }}%</span>
                        <el-button size="small" type="primary" :disabled="adopting || Math.abs(recommendedThreshold - currentThreshold) < 0.001" @click="adoptRecommended">采纳</el-button>
                      </div>
                      <div class="ds-eval__threshold-rec-reason">{{ evalResult.recommendationReason }}</div>
                    </div>
                    <div class="ds-eval__threshold-manual">
                      <span class="ds-eval__threshold-manual-label">当前阈值</span>
                      <el-input-number v-model="manualThreshold" :min="0" :max="1" :step="0.05" :precision="2" size="small" controls-position="right" style="width: 120px" />
                      <el-button size="small" :disabled="adopting || Math.abs(manualThreshold - currentThreshold) < 0.001" @click="saveManualThreshold">保存</el-button>
                    </div>
                  </div>
                </div>

                <!-- 测评用例表(融合结果列 + 行展开钻取) -->
                <el-table :data="evalQueries" v-loading="evalLoading" stripe size="small" row-key="id"
                  :expand-row-keys="expandedEvalRows"
                  @selection-change="handleEvalSelectionChange"
                  @expand-change="handleEvalExpandChange"
                  empty-text="暂无测评用例，可新增/导入，或在检索测试页点「标为期望答案」">
                  <el-table-column type="expand">
                    <template #default="{ row }">
                      <div class="ds-eval__drill" v-if="evalResultMap.get(row.id)">
                        <div class="ds-eval__drill-title">top-5 候选（精排）</div>
                        <div class="ds-eval__drill-item" v-for="(c, i) in evalResultMap.get(row.id)!.candidates" :key="i" :class="{ 'is-expected': c.expected }">
                          <span class="ds-eval__drill-rank">{{ i + 1 }}</span>
                          <span class="ds-eval__drill-text">{{ c.text }}</span>
                          <span class="ds-eval__drill-meta">
                            <el-tag v-if="c.expected" type="warning" size="small" effect="light">期望</el-tag>
                            <span class="ds-eval__drill-score">{{ (c.score * 100).toFixed(1) }}%</span>
                            <span class="ds-eval__drill-source">{{ c.source || '-' }}</span>
                          </span>
                        </div>
                        <div class="ds-eval__drill-reason" v-if="!evalResultMap.get(row.id)!.found">
                          未命中：期望文档未进入 top-5
                        </div>
                      </div>
                      <div v-else class="ds-eval__drill-empty">该用例未参与本次测评</div>
                    </template>
                  </el-table-column>
                  <el-table-column type="selection" width="42" />
                  <el-table-column label="查询" min-width="200">
                    <template #default="{ row }"><span class="ds-eval__cell-query">{{ row.query }}</span></template>
                  </el-table-column>
                  <el-table-column label="期望来源" width="140">
                    <template #default="{ row }">{{ row.expectedSource || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="期望片段（原文逐字子串）" min-width="200">
                    <template #default="{ row }"><span class="ds-eval__cell-snip">{{ row.expectedSnippet || '-' }}</span></template>
                  </el-table-column>
                  <el-table-column label="命中" width="80" align="center">
                    <template #default="{ row }">
                      <el-tag v-if="evalResultMap.get(row.id)" :type="evalResultMap.get(row.id)!.found ? 'success' : 'danger'" size="small" effect="light">
                        {{ evalResultMap.get(row.id)!.found ? '命中' : '未命中' }}
                      </el-tag>
                      <span v-else class="ds-eval__cell-dash">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="排名" width="70" align="center">
                    <template #default="{ row }">{{ evalResultMap.get(row.id)?.rank || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="top分" width="80" align="center">
                    <template #default="{ row }">
                      {{ evalResultMap.get(row.id) ? (evalResultMap.get(row.id)!.topScore * 100).toFixed(1) + '%' : '-' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="140" align="center" fixed="right">
                    <template #default="{ row }">
                      <div class="ds-eval__row-actions">
                        <el-button text size="small" type="primary" @click="openEditDialog(row)">编辑</el-button>
                        <el-button text size="small" type="danger" @click="deleteEvalQuery(row)">删除</el-button>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
                <div class="ds-search__empty" v-if="!evalResult && !evalRunning">尚未测评，勾选用例或直接点「测评」</div>
              </div>
            </div>

            <!-- 新增/编辑用例 -->
            <el-dialog v-model="evalDialogVisible" :title="evalEditing ? '编辑评测用例' : '新增评测用例'" width="640px">
              <el-form label-width="90px">
                <el-form-item label="查询">
                  <el-input v-model="evalForm.query" type="textarea" :rows="2" placeholder="评测查询（建议含口语化问题）" />
                </el-form-item>
                <el-form-item label="期望来源">
                  <el-input v-model="evalForm.expectedSource" placeholder="期望命中的文件名（可只填部分）" />
                </el-form-item>
                <el-form-item label="期望片段">
                  <el-input v-model="evalForm.expectedSnippet" type="textarea" :rows="4" placeholder="从 chunk 原文逐字摘录的子串，不可改写" />
                </el-form-item>
              </el-form>
              <template #footer>
                <el-button @click="evalDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="saveEvalQuery">保存</el-button>
              </template>
            </el-dialog>

            <!-- 导入 -->
            <el-dialog v-model="importDialogVisible" title="导入评测用例" width="720px">
              <p class="ds-eval__import-tip">粘贴外部 AI 生成的 JSON 数组：<code>[{"query":"...","expectedSource":"...","expectedSnippet":"..."}]</code></p>
              <el-input v-model="importText" type="textarea" :rows="12" placeholder='[{"query":"...","expectedSource":"...","expectedSnippet":"..."}]' />
              <template #footer>
                <el-button @click="importDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="doImport">导入</el-button>
              </template>
            </el-dialog>
          </el-tab-pane>
        </el-tabs>
      </main>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.ds-detail {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

// ---- 面包屑 ----
.ds-breadcrumb {
  padding: 16px 24px 0;
  flex-shrink: 0;
}

// ---- 主布局 ----
.ds-detail__body {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px 24px 24px;
  overflow: hidden;
}

// ---- 左侧栏 ----
.ds-sidebar {
  width: 30%;
  min-width: 280px;
  max-width: 360px;
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 14px;
  padding: 20px;
  overflow-y: auto;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  flex-shrink: 0;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  &__name {
    font-size: 17px;
    font-weight: 700;
    color: #1d1d1f;
  }

  &__desc {
    font-size: 13px;
    color: #86868b;
    margin-bottom: 16px;
    line-height: 1.5;
  }

  &__section {
    margin-bottom: 18px;
    padding-top: 14px;
    border-top: 1px solid rgba(0, 0, 0, 0.06);
  }

  &__section-title {
    font-size: 11px;
    font-weight: 600;
    color: #aeaeb2;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 10px;
  }

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 5px 0;
    font-size: 13px;
  }

  &__label {
    color: #86868b;
  }

  &__value {
    color: #1d1d1f;
    font-weight: 500;
  }

  &__code {
    font-family: 'SF Mono', 'Fira Code', monospace;
    font-size: 11.5px;
    background: rgba(0, 113, 227, 0.07);
    color: #0071e3;
    padding: 2px 6px;
    border-radius: 4px;
  }

  &__stats {
    display: flex;
    gap: 24px;
  }

  &__stat {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__stat-num {
    font-size: 20px;
    font-weight: 700;
    color: #1d1d1f;
  }

  &__stat-label {
    font-size: 11px;
    color: #aeaeb2;
  }
}

// ---- 右侧主区域 ----
.ds-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.ds-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  :deep(.el-tabs__header) {
    flex-shrink: 0;
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }
}

// ---- 玻璃卡片 ----
.glass-card {
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 14px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  margin-bottom: 14px;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 18px 0;
    font-size: 14px;
    font-weight: 600;
    color: #1d1d1f;
    flex-wrap: wrap;
    gap: 8px;
  }

  &__body {
    padding: 14px 18px;
  }
}

// ---- 轮询控制栏 ----
.polling-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;

  .polling-label {
    font-size: 12px;
    color: #86868b;
    font-weight: 400;
  }
}

.ds-upload-card {
  flex-shrink: 0;
}

.ds-file-list-card {
  flex: 1;
  display: flex;
  flex-direction: column;

  .glass-card__body {
    flex: 1;
    display: flex;
    flex-direction: column;
  }
}

// ---- 操作按钮 ----
.action-btns {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

// ---- 文件名单元格 ----
.doc-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  .el-icon { color: var(--el-color-primary); flex-shrink: 0; }

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.ds-file-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(210, 210, 215, 0.3);
}

// ---- 进度条单元格 ----
.progress-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.progress-step {
  font-size: 11px;
  color: #86868b;
  white-space: nowrap;
}

// ---- 检索测试 ----
.ds-search-card {
  min-height: 300px;
}

.ds-search__input-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.ds-search__input-row-inner {
  display: flex;
  flex: 1;
  gap: 10px;
  align-items: center;
}

.ds-search__topk-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 0 10px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 8px;
  height: 36px;
}

.ds-search__topk-label {
  font-size: 13px;
  font-weight: 500;
  color: #86868b;
  white-space: nowrap;
}

.ds-search__help-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: rgba(0, 113, 227, 0.1);
  color: #0071e3;
  font-size: 11px;
  cursor: help;
  flex-shrink: 0;
  transition: background 0.2s;

  &:hover {
    background: rgba(0, 113, 227, 0.2);
  }
}

.ds-search__results {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ds-search__item {
  padding: 14px 16px;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 10px;
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.ds-search__item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.ds-search__score {
  font-size: 12px;
  color: #0071e3;
  font-weight: 500;
}

.ds-search__reranker-status {
  margin: 10px 0 4px;
  display: flex;
  align-items: center;
}

.ds-search__threshold-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #86868b;
}

.ds-search__item-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ds-search__content {
  font-size: 13.5px;
  line-height: 1.6;
  color: #1d1d1f;
  white-space: pre-wrap;
}

.ds-search__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(0, 0, 0, 0.04);
}

.ds-search__meta-tag {
  font-size: 11px;
  color: #86868b;
  background: rgba(0, 0, 0, 0.04);
  padding: 2px 8px;
  border-radius: 4px;
}

.ds-search__empty {
  text-align: center;
  padding: 40px;
  color: #aeaeb2;
  font-size: 14px;
}

.ds-search__hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 48px 20px;
  color: #c7c7cc;
  text-align: center;

  span { font-size: 14px; }

  &-sub {
    font-size: 12px !important;
    color: #d2d2d7 !important;
  }
}

// ---- 检索评测 ----
.ds-eval-card {
  min-height: 300px;
}

.ds-eval__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ds-eval__progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.ds-eval__progress-text {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.ds-eval__row-actions {
  display: inline-flex;
  gap: 4px;
  justify-content: center;
  white-space: nowrap;
}

.ds-eval__selected {
  margin-left: 4px;
  font-size: 12px;
  color: var(--el-color-primary);
}

.ds-eval__summary {
  margin-bottom: 14px;
}

.ds-eval__summary-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.ds-eval__expand-toggle {
  flex-shrink: 0;
}

.ds-eval__scan-collapse {
  margin-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.ds-eval__scan-collapse :deep(.el-collapse-item__header) {
  font-size: 13px;
  font-weight: 500;
  height: 36px;
  line-height: 36px;
}

.ds-eval__scan-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.ds-eval__cell-dash {
  color: var(--el-text-color-placeholder);
}

.ds-eval__drill {
  padding: 4px 8px;
}

.ds-eval__drill-title {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ds-eval__drill-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 8px;
  margin-bottom: 4px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.ds-eval__drill-item.is-expected {
  background: rgba(230, 162, 60, 0.12);
  outline: 1px solid rgba(230, 162, 60, 0.4);
}

.ds-eval__drill-rank {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--el-color-info-light-7);
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  color: var(--el-text-color-regular);
}

.ds-eval__drill-text {
  flex: 1;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ds-eval__drill-meta {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.ds-eval__drill-score {
  font-weight: 600;
  color: var(--el-color-primary);
}

.ds-eval__drill-source {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ds-eval__drill-reason {
  margin-top: 6px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(245, 108, 108, 0.1);
  font-size: 12px;
  color: var(--el-color-danger);
}

.ds-eval__drill-empty {
  padding: 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.ds-eval__prompt {
  margin-bottom: 14px;
  padding: 12px 14px;
  background: rgba(0, 113, 227, 0.05);
  border: 1px dashed rgba(0, 113, 227, 0.3);
  border-radius: 8px;
}

.ds-eval__prompt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12.5px;
  color: #6e6e73;
}

.ds-eval__prompt-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #1d1d1f;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.ds-eval__cell-query,
.ds-eval__cell-snip {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  color: #1d1d1f;
}

.ds-eval__result {
  margin-top: 18px;
}

.ds-eval__metrics {
  display: flex;
  gap: 32px;
  padding: 16px 20px;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 10px;
  margin-bottom: 18px;
}

.ds-eval__metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ds-eval__metric-num {
  font-size: 24px;
  font-weight: 700;
  color: #0071e3;
}

.ds-eval__metric-label {
  font-size: 11px;
  color: #86868b;
}

.ds-eval__scan {
  margin-bottom: 18px;
}

.ds-eval__scan-title {
  font-size: 12.5px;
  color: #6e6e73;
  margin-bottom: 10px;
}

.ds-eval__scan-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.ds-eval__scan-label {
  width: 84px;
  font-size: 12px;
  color: #86868b;
  flex-shrink: 0;

  &.is-current {
    color: #0071e3;
    font-weight: 600;
  }

  &.is-recommended {
    color: #34c759;
    font-weight: 600;
  }
}

.ds-eval__scan-bar-bg {
  flex: 1;
  height: 14px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 7px;
  overflow: hidden;
}

.ds-eval__scan-bar {
  height: 100%;
  background: linear-gradient(90deg, #34c759, #30d158);
  border-radius: 7px;
  transition: width 0.3s;
}

.ds-eval__scan-value {
  width: 40px;
  font-size: 12px;
  color: #1d1d1f;
  font-weight: 500;
  text-align: right;
  flex-shrink: 0;
}

// ---- 推荐阈值面板(采纳 + 手动微调) ----
.ds-eval__threshold-panel {
  margin-top: 12px;
  padding: 12px 14px;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ds-eval__threshold-rec-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ds-eval__threshold-rec-label {
  font-size: 12px;
  color: #86868b;
}

.ds-eval__threshold-rec-value {
  font-size: 18px;
  font-weight: 700;
  color: #34c759;
}

.ds-eval__threshold-rec-reason {
  margin-top: 6px;
  font-size: 12px;
  color: #6e6e73;
  line-height: 1.5;
}

.ds-eval__threshold-manual {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ds-eval__threshold-manual-label {
  font-size: 12px;
  color: #86868b;
}

.ds-eval__detail-title {
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 8px;
}

.ds-eval__import-tip {
  font-size: 12.5px;
  color: #6e6e73;
  margin-bottom: 10px;

  code {
    background: rgba(0, 0, 0, 0.05);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 11.5px;
  }
}

// ---- Loading 旋转图标 ----
.is-loading {
  animation: rotating 1.2s linear infinite;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
