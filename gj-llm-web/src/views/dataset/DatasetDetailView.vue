<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadInstance, UploadRawFile } from 'element-plus'
import { datasetApi } from '@/api/modules/dataset'
import type { Dataset, DatasetFile, SearchResultItem } from '@/api/types'

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
const activeTab = ref<'files' | 'search'>('files')
const searchQuery = ref('')
const searchTopK = ref(3)
const searching = ref(false)
const searchResults = ref<SearchResultItem[]>([])

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
  } catch { /* 拦截器统一处理 */ } finally {
    uploading.value = false
    uploadRef.value?.clearFiles()
  }
}

// ---- 删除文档 ----
async function handleDeleteDoc(row: DatasetFile) {
  try {
    await ElMessageBox.confirm(
      `确定要删除 "${row.fileName}" 吗？将同时移除向量数据。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch { return }

  try {
    await datasetApi.deleteDocument(datasetId, row.id)
    ElMessage.success('删除成功')
    if (docList.value.length === 1 && docPage.value > 1) docPage.value--
    await loadDocuments()
    await loadDataset()
  } catch { /* 拦截器统一处理 */ }
}

// ---- 检索测试 ----
async function handleSearch() {
  if (!searchQuery.value.trim()) return
  searching.value = true
  searchResults.value = []
  try {
    const res = await datasetApi.testSearch(datasetId, searchQuery.value, searchTopK.value)
    searchResults.value = res.data.data || []
  } finally {
    searching.value = false
  }
}

// ---- 重新解析 ----
async function handleReParse(row: DatasetFile) {
  try {
    await datasetApi.reparseDocument(datasetId, row.id)
    ElMessage.success('已触发重新解析')
    pollUntilDone(row.id)
  } catch { /* 拦截器统一处理 */ }
}

function pollUntilDone(dfId: number) {
  const timer = setInterval(async () => {
    await loadDocuments()
    await loadDataset()
    const file = docList.value.find((f) => f.id === dfId)
    if (!file || file.status === 'COMPLETED' || file.status === 'FAILED') {
      clearInterval(timer)
      if (file?.status === 'COMPLETED') ElMessage.success('重新解析完成')
      else if (file?.status === 'FAILED') ElMessage.error('重新解析失败：' + (file.errorMessage || '未知错误'))
    }
  }, 2000)
}

onMounted(() => {
  loadDataset()
  loadDocuments()
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
                <el-button text size="small" @click="loadDocuments">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
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
                  <el-table-column label="处理状态" width="120" align="center">
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
                      <el-tag v-else :type="statusType(row.status)" size="small" effect="light">
                        <el-icon v-if="row.status === 'PROCESSING'" class="is-loading"><Loading /></el-icon>
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
                        <el-button text size="small" type="danger" @click="handleDeleteDoc(row)">
                          <el-icon><Delete /></el-icon>
                          删除
                        </el-button>
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
                  <el-input
                    v-model="searchQuery"
                    placeholder="输入问题，测试 RAG 召回效果..."
                    clearable
                    @keyup.enter="handleSearch"
                    style="flex: 1"
                  >
                    <template #prepend>
                      <el-select v-model="searchTopK" style="width: 90px">
                        <el-option :value="3" label="Top 3" />
                        <el-option :value="5" label="Top 5" />
                        <el-option :value="10" label="Top 10" />
                      </el-select>
                    </template>
                  </el-input>
                  <el-button type="primary" :loading="searching" @click="handleSearch">
                    <el-icon><Search /></el-icon>
                    检索
                  </el-button>
                </div>

                <div class="ds-search__results" v-if="searchResults.length > 0">
                  <div
                    v-for="item in searchResults"
                    :key="item.rank"
                    class="ds-search__item"
                  >
                    <div class="ds-search__item-header">
                      <el-tag size="small" effect="dark" round>#{{ item.rank }}</el-tag>
                      <span class="ds-search__score">
                        相似度：{{ (item.score * 100).toFixed(1) }}%
                      </span>
                    </div>
                    <div class="ds-search__content">{{ item.content }}</div>
                    <div class="ds-search__meta" v-if="item.metadata && Object.keys(item.metadata).length">
                      <span
                        v-for="(val, key) in item.metadata"
                        :key="key"
                        class="ds-search__meta-tag"
                      >
                        {{ key }}: {{ val }}
                      </span>
                    </div>
                  </div>
                </div>

                <div class="ds-search__empty" v-else-if="!searching && searchQuery">
                  暂无结果
                </div>

                <div class="ds-search__hint" v-if="!searchQuery && searchResults.length === 0">
                  <el-icon :size="36"><Search /></el-icon>
                  <span>输入查询内容测试知识库的检索效果</span>
                  <span class="ds-search__hint-sub">不需启动对话即可验证 RAG 召回质量</span>
                </div>
              </div>
            </div>
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

  :deep(.el-tabs__content) {
    flex: 1;
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
  }

  &__body {
    padding: 14px 18px;
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

// ---- 检索测试 ----
.ds-search-card {
  min-height: 300px;
}

.ds-search__input-row {
  display: flex;
  gap: 10px;
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

// ---- Loading 旋转图标 ----
.is-loading {
  animation: rotating 1.2s linear infinite;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
